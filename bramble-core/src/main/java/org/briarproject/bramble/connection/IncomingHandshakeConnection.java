package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.contact.HandshakeManager;
import org.briarproject.bramble.api.contact.HandshakeManager.HandshakeResult;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class IncomingHandshakeConnection extends HandshakeConnection
		implements Runnable {

	IncomingHandshakeConnection(KeyManager keyManager,
			ConnectionRegistry connectionRegistry,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			HandshakeManager handshakeManager,
			ContactExchangeManager contactExchangeManager,
			ConnectionManager connectionManager,
			PendingContactId pendingContactId,
			TransportId transportId, DuplexTransportConnection connection) {
		super(keyManager, connectionRegistry, streamReaderFactory,
				streamWriterFactory, handshakeManager, contactExchangeManager,
				connectionManager, pendingContactId, transportId, connection);
	}

	@Override
	public void run() {
		// Read and recognise the tag
		StreamContext ctxIn = recogniseTag(reader, transportId);
		if (ctxIn == null) {
			LOG.info("Unrecognised tag");
			onError(false);
			return;
		}
		PendingContactId inPendingContactId = ctxIn.getPendingContactId();
		if (inPendingContactId == null) {
			LOG.warning("Expected rendezvous tag, got contact tag");
			onError(true);
			return;
		}
		// Allocate the outgoing stream context
		StreamContext ctxOut =
				allocateStreamContext(pendingContactId, transportId);
		if (ctxOut == null) {
			LOG.warning("Could not allocate stream context");
			onError(true);
			return;
		}
		// Close the connection if it's redundant
		if (!connectionRegistry.registerConnection(pendingContactId)) {
			LOG.info("Redundant rendezvous connection");
			onError(true);
			return;
		}
		// Handshake and exchange contacts
		try {
			InputStream in = streamReaderFactory.createStreamReader(
					reader.getInputStream(), ctxIn);
			// Flush the output stream to send the outgoing stream header
			StreamWriter out = streamWriterFactory.createStreamWriter(
					writer.getOutputStream(), ctxOut);
			out.getOutputStream().flush();
			HandshakeResult result =
					handshakeManager.handshake(pendingContactId, in, out);
			contactExchangeManager.exchangeContacts(pendingContactId,
					connection, result.getMasterKey(), result.isAlice(), false);
			connectionRegistry.unregisterConnection(pendingContactId, true);
			// Reuse the connection as a transport connection
			connectionManager.manageIncomingConnection(transportId, connection);
		} catch (IOException | DbException e) {
			logException(LOG, WARNING, e);
			onError(true);
			connectionRegistry.unregisterConnection(pendingContactId, false);
		}
	}
}
