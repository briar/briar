package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.KeyAgreementCrypto;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.TransportDescriptor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.CONNECTION_TIMEOUT;

@NotNullByDefault
class KeyAgreementConnector {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementConnector.class.getName());

	private final Clock clock;
	private final KeyAgreementCrypto keyAgreementCrypto;
	private final PluginManager pluginManager;
	private final Executor ioExecutor;
	private final ConnectionChooser connectionChooser;

	private final List<KeyAgreementListener> listeners =
			new CopyOnWriteArrayList<>();

	private volatile boolean stopped = false;

	KeyAgreementConnector(Clock clock, KeyAgreementCrypto keyAgreementCrypto,
			PluginManager pluginManager, Executor ioExecutor,
			ConnectionChooser connectionChooser) {
		this.clock = clock;
		this.keyAgreementCrypto = keyAgreementCrypto;
		this.pluginManager = pluginManager;
		this.ioExecutor = ioExecutor;
		this.connectionChooser = connectionChooser;
	}

	Payload listen(KeyPair localKeyPair) {
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
				if (LOG.isLoggable(INFO)) LOG.info("Listening via " + id);
				listeners.add(l);
				ioExecutor.execute(() -> {
					try {
						connectionChooser.addConnection(l.accept());
					} catch (IOException e) {
						if (LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
					}
				});
			}
		}
		return new Payload(commitment, descriptors);
	}

	void stopListening() {
		LOG.info("Stopping BQP listeners");
		stopped = true;
		for (KeyAgreementListener l : listeners) l.close();
		listeners.clear();
		connectionChooser.stop();
	}

	@Nullable
	public KeyAgreementTransport connect(Payload remotePayload, boolean alice) {
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
				ioExecutor.execute(() -> {
					try {
						KeyAgreementConnection c =
								connect(plugin, commitment, descriptor);
						if (c != null) connectionChooser.addConnection(c);
					} catch (InterruptedException e) {
						LOG.info("Interrupted while waiting to connect");
					}
				});
			}
		}

		// Get chosen connection
		try {
			KeyAgreementConnection chosen = connectionChooser.chooseConnection(
					alice, CONNECTION_TIMEOUT);
			if (chosen == null) return null; // No suitable connection
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

	@Nullable
	@IoExecutor
	private KeyAgreementConnection connect(DuplexPlugin plugin,
			byte[] commitment, BdfList descriptor) throws InterruptedException {
		// Repeat attempts until we time out, get stopped, or get interrupted
		long end = clock.currentTimeMillis() + CONNECTION_TIMEOUT;
		while (!stopped && clock.currentTimeMillis() < end) {
			DuplexTransportConnection conn =
					plugin.createKeyAgreementConnection(commitment, descriptor);
			if (conn != null)
				return new KeyAgreementConnection(conn, plugin.getId());
			// Wait 2s before retry (to circumvent transient failures)
			Thread.sleep(2000);
		}
		return null;
	}
}
