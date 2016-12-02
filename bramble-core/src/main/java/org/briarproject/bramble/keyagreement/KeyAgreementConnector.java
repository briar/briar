package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
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
	private final CryptoComponent crypto;
	private final PluginManager pluginManager;
	private final CompletionService<KeyAgreementConnection> connect;

	private final List<KeyAgreementListener> listeners =
			new ArrayList<KeyAgreementListener>();
	private final List<Future<KeyAgreementConnection>> pending =
			new ArrayList<Future<KeyAgreementConnection>>();

	private volatile boolean connecting = false;
	private volatile boolean alice = false;

	KeyAgreementConnector(Callbacks callbacks, Clock clock,
			CryptoComponent crypto, PluginManager pluginManager,
			Executor ioExecutor) {
		this.callbacks = callbacks;
		this.clock = clock;
		this.crypto = crypto;
		this.pluginManager = pluginManager;
		connect = new ExecutorCompletionService<KeyAgreementConnection>(
				ioExecutor);
	}

	public Payload listen(KeyPair localKeyPair) {
		LOG.info("Starting BQP listeners");
		// Derive commitment
		byte[] commitment = crypto.deriveKeyCommitment(
				localKeyPair.getPublic().getEncoded());
		// Start all listeners and collect their descriptors
		List<TransportDescriptor> descriptors =
				new ArrayList<TransportDescriptor>();
		for (DuplexPlugin plugin : pluginManager.getKeyAgreementPlugins()) {
			KeyAgreementListener l =
					plugin.createKeyAgreementListener(commitment);
			if (l != null) {
				TransportId id = plugin.getId();
				descriptors.add(new TransportDescriptor(id, l.getDescriptor()));
				pending.add(connect.submit(new ReadableTask(l.listen())));
				listeners.add(l);
			}
		}
		return new Payload(commitment, descriptors);
	}

	void stopListening() {
		LOG.info("Stopping BQP listeners");
		for (KeyAgreementListener l : listeners) {
			l.close();
		}
		listeners.clear();
	}

	@Nullable
	public KeyAgreementTransport connect(Payload remotePayload,
			boolean alice) {
		// Let the listeners know if we are Alice
		this.connecting = true;
		this.alice = alice;
		long end = clock.currentTimeMillis() + CONNECTION_TIMEOUT;

		// Start connecting over supported transports
		LOG.info("Starting outgoing BQP connections");
		for (TransportDescriptor d : remotePayload.getTransportDescriptors()) {
			Plugin p = pluginManager.getPlugin(d.getId());
			if (p instanceof DuplexPlugin) {
				DuplexPlugin plugin = (DuplexPlugin) p;
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
			if (f == null)
				return null; // No task completed within the timeout.
			chosen = f.get();
			return new KeyAgreementTransport(chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for connection");
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			stopListening();
			// Close all other connections
			closePending(chosen);
		}
	}

	private void closePending(@Nullable KeyAgreementConnection chosen) {
		for (Future<KeyAgreementConnection> f : pending) {
			try {
				if (f.cancel(true)) {
					LOG.info("Cancelled task");
				} else if (!f.isCancelled()) {
					KeyAgreementConnection c = f.get();
					if (c != null && c != chosen)
						tryToClose(c.getConnection(), false);
				}
			} catch (InterruptedException e) {
				LOG.info("Interrupted while closing sockets");
				Thread.currentThread().interrupt();
				return;
			} catch (ExecutionException e) {
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
			}
		}
	}

	private void tryToClose(DuplexTransportConnection conn, boolean exception) {
		try {
			if (LOG.isLoggable(INFO))
				LOG.info("Closing connection, exception: " + exception);
			conn.getReader().dispose(exception, true);
			conn.getWriter().dispose(exception);
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
		public KeyAgreementConnection call() throws Exception {
			// Repeat attempts until we connect, get interrupted, or time out
			while (true) {
				long now = clock.currentTimeMillis();
				if (now > end) throw new IOException();
				DuplexTransportConnection conn =
						plugin.createKeyAgreementConnection(commitment,
								descriptor, end - now);
				if (conn != null) {
					if (LOG.isLoggable(INFO))
						LOG.info(plugin.getId().getString() +
								": Outgoing connection");
					return new KeyAgreementConnection(conn, plugin.getId());
				}
				// Wait 2s before retry (to circumvent transient failures)
				Thread.sleep(2000);
			}
		}
	}

	private class ReadableTask
			implements Callable<KeyAgreementConnection> {

		private final Callable<KeyAgreementConnection> connectionTask;

		private ReadableTask(Callable<KeyAgreementConnection> connectionTask) {
			this.connectionTask = connectionTask;
		}

		@Override
		public KeyAgreementConnection call() throws Exception {
			KeyAgreementConnection c = connectionTask.call();
			InputStream in = c.getConnection().getReader().getInputStream();
			boolean waitingSent = false;
			while (!alice && in.available() == 0) {
				if (!waitingSent && connecting && !alice) {
					// Bob waits here until Alice obtains his payload.
					callbacks.connectionWaiting();
					waitingSent = true;
				}
				if (LOG.isLoggable(INFO)) {
					LOG.info(c.getTransportId().getString() +
							": Waiting for connection");
				}
				Thread.sleep(1000);
			}
			if (!alice && LOG.isLoggable(INFO))
				LOG.info(c.getTransportId().getString() + ": Data available");
			return c;
		}
	}
}
