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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;

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
	private final CryptoComponent crypto;
	private final PluginManager pluginManager;
	private final ConnectionChooser connectionChooser;

	private final List<KeyAgreementListener> listeners =
			new CopyOnWriteArrayList<>();
	private final CountDownLatch aliceLatch = new CountDownLatch(1);
	private final AtomicBoolean waitingSent = new AtomicBoolean(false);

	private volatile boolean alice = false, stopped = false;

	KeyAgreementConnector(Callbacks callbacks,
			CryptoComponent crypto, PluginManager pluginManager,
			ConnectionChooser connectionChooser) {
		this.callbacks = callbacks;
		this.crypto = crypto;
		this.pluginManager = pluginManager;
		this.connectionChooser = connectionChooser;
	}

	Payload listen(KeyPair localKeyPair) {
		LOG.info("Starting BQP listeners");
		// Derive commitment
		byte[] commitment = crypto.deriveKeyCommitment(
				localKeyPair.getPublic().getEncoded());
		// Start all listeners and collect their descriptors
		List<TransportDescriptor> descriptors = new ArrayList<>();
		for (DuplexPlugin plugin : pluginManager.getKeyAgreementPlugins()) {
			KeyAgreementListener l =
					plugin.createKeyAgreementListener(commitment);
			if (l != null) {
				TransportId id = plugin.getId();
				descriptors.add(new TransportDescriptor(id, l.getDescriptor()));
				if (LOG.isLoggable(INFO)) LOG.info("Listening via " + id);
				listeners.add(l);
				connectionChooser.submit(new ReadableTask(l::accept));
			}
		}
		return new Payload(commitment, descriptors);
	}

	void stopListening() {
		LOG.info("Stopping BQP listeners");
		stopped = true;
		aliceLatch.countDown();
		for (KeyAgreementListener l : listeners) l.close();
		connectionChooser.stop();
	}

	@Nullable
	public KeyAgreementTransport connect(Payload remotePayload, boolean alice) {
		// Let the ReadableTasks know if we are Alice
		this.alice = alice;
		aliceLatch.countDown();

		// Start connecting over supported transports
		if (LOG.isLoggable(INFO)) {
			LOG.info("Starting outgoing BQP connections as "
					+ (alice ? "Alice" : "Bob"));
		}
		for (TransportDescriptor d : remotePayload.getTransportDescriptors()) {
			Plugin p = pluginManager.getPlugin(d.getId());
			if (p instanceof DuplexPlugin) {
				if (LOG.isLoggable(INFO))
					LOG.info("Connecting via " + d.getId());
				DuplexPlugin plugin = (DuplexPlugin) p;
				byte[] commitment = remotePayload.getCommitment();
				BdfList descriptor = d.getDescriptor();
				connectionChooser.submit(new ReadableTask(
						new ConnectorTask(plugin, commitment, descriptor)));
			}
		}

		// Get chosen connection
		try {
			KeyAgreementConnection chosen =
					connectionChooser.poll(CONNECTION_TIMEOUT);
			if (chosen == null) return null;
			return new KeyAgreementTransport(chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for connection");
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			stopListening();
		}
	}

	private void waitingForAlice() {
		if (!waitingSent.getAndSet(true)) callbacks.connectionWaiting();
	}

	private class ConnectorTask implements Callable<KeyAgreementConnection> {

		private final byte[] commitment;
		private final BdfList descriptor;
		private final DuplexPlugin plugin;

		private ConnectorTask(DuplexPlugin plugin, byte[] commitment,
				BdfList descriptor) {
			this.plugin = plugin;
			this.commitment = commitment;
			this.descriptor = descriptor;
		}

		@Nullable
		@Override
		public KeyAgreementConnection call() throws Exception {
			// Repeat attempts until we connect, get stopped, or get interrupted
			while (!stopped) {
				DuplexTransportConnection conn =
						plugin.createKeyAgreementConnection(commitment,
								descriptor);
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

		@Nullable
		@Override
		public KeyAgreementConnection call() throws Exception {
			KeyAgreementConnection c = connectionTask.call();
			if (c == null) return null;
			aliceLatch.await();
			if (alice || stopped) return c;
			// Bob waits here for Alice to scan his QR code, determine her
			// role, and send her key
			InputStream in = c.getConnection().getReader().getInputStream();
			while (!stopped && in.available() == 0) {
				if (LOG.isLoggable(INFO))
					LOG.info(c.getTransportId() + ": Waiting for data");
				waitingForAlice();
				Thread.sleep(500);
			}
			if (!stopped && LOG.isLoggable(INFO))
				LOG.info(c.getTransportId().getString() + ": Data available");
			return c;
		}
	}
}
