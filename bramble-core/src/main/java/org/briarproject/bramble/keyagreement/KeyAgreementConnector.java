package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.KeyAgreementCrypto;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.TransportDescriptor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.CONNECTION_TIMEOUT;

@NotNullByDefault
class KeyAgreementConnector {

	interface Callbacks {
		void connectionWaiting();
	}

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementConnector.class.getName());

	private final Callbacks callbacks;
	private final Clock clock;
	private final KeyAgreementCrypto keyAgreementCrypto;
	private final PluginManager pluginManager;
	private final Executor ioExecutor;
	private final CompletionService<KeyAgreementConnection> connect;

	private final List<KeyAgreementListener> listeners = new ArrayList<>();
	private final List<Future<KeyAgreementConnection>> pending =
			new ArrayList<>();

	private volatile boolean connecting = false;
	private volatile boolean alice = false;
	private volatile boolean stopped = false;

	KeyAgreementConnector(Callbacks callbacks, Clock clock,
			KeyAgreementCrypto keyAgreementCrypto, PluginManager pluginManager,
			Executor ioExecutor) {
		this.callbacks = callbacks;
		this.clock = clock;
		this.keyAgreementCrypto = keyAgreementCrypto;
		this.pluginManager = pluginManager;
		this.ioExecutor = ioExecutor;
		connect = new ExecutorCompletionService<>(ioExecutor);
	}

	public Payload listen(KeyPair localKeyPair) {
		LOG.info("Starting BQP listeners");
		// Derive commitment
		byte[] commitment = keyAgreementCrypto.deriveKeyCommitment(
				localKeyPair.getPublic());
		// Start all listeners and collect their descriptors
		List<TransportDescriptor> descriptors = new ArrayList<>();
		for (DuplexPlugin plugin : pluginManager.getKeyAgreementPlugins()) {
			KeyAgreementListener l =
					plugin.createKeyAgreementListener(commitment);
			if (l != null) {
				TransportId id = plugin.getId();
				descriptors.add(new TransportDescriptor(id, l.getDescriptor()));
				if (LOG.isLoggable(INFO))
					LOG.info("Creating incoming task for " + id);
				pending.add(connect.submit(new ReadableTask(l.listen())));
				listeners.add(l);
			}
		}
		return new Payload(commitment, descriptors);
	}

	void stopListening() {
		LOG.info("Stopping BQP listeners");
		stopped = true;
		for (KeyAgreementListener l : listeners) {
			l.close();
		}
		listeners.clear();
	}

	@Nullable
	public KeyAgreementTransport connect(Payload remotePayload, boolean alice) {
		// Let the ReadableTasks know if we are Alice
		this.connecting = true;
		this.alice = alice;
		long end = clock.currentTimeMillis() + CONNECTION_TIMEOUT;

		// Start connecting over supported transports
		if (LOG.isLoggable(INFO)) {
			LOG.info("Starting outgoing BQP connections as "
					+ (alice ? "Alice" : "Bob"));
		}
		for (TransportDescriptor d : remotePayload.getTransportDescriptors()) {
			Plugin p = pluginManager.getPlugin(d.getId());
			if (p instanceof DuplexPlugin) {
				DuplexPlugin plugin = (DuplexPlugin) p;
				if (LOG.isLoggable(INFO))
					LOG.info("Creating outgoing task for " + d.getId());
				pending.add(connect.submit(new ReadableTask(
						new ConnectorTask(plugin, remotePayload.getCommitment(),
								d.getDescriptor(), end))));
			}
		}

		// Get chosen connection
		KeyAgreementConnection chosen = null;
		try {
			long now = clock.currentTimeMillis();
			Future<KeyAgreementConnection> f =
					connect.poll(end - now, MILLISECONDS);
			if (f == null) return null; // No task completed within the timeout
			chosen = f.get();
			if (chosen == null) return null; // We've been stopped
			return new KeyAgreementTransport(chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for connection");
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException | IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			stopListening();
			// Close all other connections
			closePending(chosen);
		}
	}

	private void closePending(@Nullable KeyAgreementConnection chosen) {
		List<Future<KeyAgreementConnection>> unfinished = new ArrayList<>();
		try {
			for (Future<KeyAgreementConnection> f : pending) {
				if (f.isDone()) {
					LOG.info("Task is already done");
					closeIfNotChosen(f, chosen);
				} else {
					LOG.info("Task is not done");
					unfinished.add(f);
				}
			}
		} catch (InterruptedException e) {
			LOG.info("Interrupted while closing connections");
			Thread.currentThread().interrupt();
		}
		for (Future<KeyAgreementConnection> f : unfinished) {
			ioExecutor.execute(() -> {
				try {
					closeIfNotChosen(f, chosen);
				} catch (InterruptedException e) {
					LOG.info("Interrupted while closing connections");
				}
			});
		}
	}

	private void closeIfNotChosen(Future<KeyAgreementConnection> f,
			@Nullable KeyAgreementConnection chosen)
			throws InterruptedException {
		try {
			KeyAgreementConnection c = f.get();
			if (c == null) {
				LOG.info("Result is null");
			} else if (c == chosen) {
				LOG.info("Not closing chosen connection");
			} else {
				LOG.info("Closing unchosen connection");
				tryToClose(c.getConnection());
			}
		} catch (ExecutionException e) {
			if (LOG.isLoggable(INFO))
				LOG.info("Task threw exception: " + e);
		}
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getReader().dispose(false, true);
			conn.getWriter().dispose(false);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info(e.toString());
		}
	}

	private class ConnectorTask implements Callable<KeyAgreementConnection> {

		private final byte[] commitment;
		private final BdfList descriptor;
		private final long end;
		private final DuplexPlugin plugin;

		private ConnectorTask(DuplexPlugin plugin, byte[] commitment,
				BdfList descriptor, long end) {
			this.plugin = plugin;
			this.commitment = commitment;
			this.descriptor = descriptor;
			this.end = end;
		}

		@Override
		@Nullable
		public KeyAgreementConnection call() throws Exception {
			// Repeat attempts until we connect, get interrupted, or time out
			while (!stopped) {
				long now = clock.currentTimeMillis();
				if (now >= end) throw new IOException("Timed out");
				DuplexTransportConnection conn =
						plugin.createKeyAgreementConnection(commitment,
								descriptor, end - now);
				if (conn != null) {
					if (LOG.isLoggable(INFO))
						LOG.info(plugin.getId() + ": Outgoing connection");
					return new KeyAgreementConnection(conn, plugin.getId());
				}
				// Wait 2s before retry (to circumvent transient failures)
				Thread.sleep(2000);
			}
			return null;
		}
	}

	private class ReadableTask implements Callable<KeyAgreementConnection> {

		private final Callable<KeyAgreementConnection> connectionTask;

		private ReadableTask(Callable<KeyAgreementConnection> connectionTask) {
			this.connectionTask = connectionTask;
		}

		@Override
		@Nullable
		public KeyAgreementConnection call() throws Exception {
			KeyAgreementConnection c = connectionTask.call();
			if (c == null) return null;
			InputStream in = c.getConnection().getReader().getInputStream();
			boolean waitingSent = false;
			try {
				while (!stopped && !alice && in.available() == 0) {
					if (!waitingSent && connecting && !alice) {
						// Bob waits here until Alice obtains his payload.
						callbacks.connectionWaiting();
						waitingSent = true;
					}
					if (LOG.isLoggable(INFO))
						LOG.info(c.getTransportId() + ": Waiting for data");
					Thread.sleep(1000);
				}
			} catch (IOException | InterruptedException e) {
				if (LOG.isLoggable(INFO)) LOG.info("Closing connection: " + e);
				tryToClose(c.getConnection());
				throw e;
			}
			if (!stopped && !alice && LOG.isLoggable(INFO))
				LOG.info(c.getTransportId() + ": Data available");
			return c;
		}
	}
}
