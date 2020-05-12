package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.util.concurrent.Executor;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

class OutgoingDuplexSyncConnection extends DuplexSyncConnection
		implements Runnable {

	private final ContactId contactId;

	OutgoingDuplexSyncConnection(KeyManager keyManager,
			ConnectionRegistry connectionRegistry,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			TransportPropertyManager transportPropertyManager,
			Executor ioExecutor, ContactId contactId, TransportId transportId,
			DuplexTransportConnection connection) {
		super(keyManager, connectionRegistry, streamReaderFactory,
				streamWriterFactory, syncSessionFactory,
				transportPropertyManager, ioExecutor, transportId, connection);
		this.contactId = contactId;
	}

	@Override
	public void run() {
		// Allocate a stream context
		StreamContext ctx = allocateStreamContext(contactId, transportId);
		if (ctx == null) {
			LOG.warning("Could not allocate stream context");
			onWriteError();
			return;
		}
		if (ctx.isHandshakeMode()) {
			// TODO: Support handshake mode for contacts
			LOG.warning("Cannot use handshake mode stream context");
			onWriteError();
			return;
		}
		// Start the incoming session on another thread
		ioExecutor.execute(this::runIncomingSession);
		try {
			// Create and run the outgoing session
			SyncSession out = createDuplexOutgoingSession(ctx, writer);
			outgoingSession = out;
			out.run();
			writer.dispose(false);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			onWriteError();
		}
	}

	private void runIncomingSession() {
		// Read and recognise the tag
		StreamContext ctx = recogniseTag(reader, transportId);
		// Unrecognised tags are suspicious in this case
		if (ctx == null) {
			LOG.warning("Unrecognised tag for returning stream");
			onReadError();
			return;
		}
		// Check that the stream comes from the expected contact
		ContactId inContactId = ctx.getContactId();
		if (inContactId == null) {
			LOG.warning("Expected contact tag, got rendezvous tag");
			onReadError();
			return;
		}
		if (!contactId.equals(inContactId)) {
			LOG.warning("Wrong contact ID for returning stream");
			onReadError();
			return;
		}
		if (ctx.isHandshakeMode()) {
			// TODO: Support handshake mode for contacts
			LOG.warning("Received handshake tag, expected rotation mode");
			onReadError();
			return;
		}
		connectionRegistry.registerConnection(contactId, transportId, false);
		try {
			// Store any transport properties discovered from the connection
			transportPropertyManager.addRemotePropertiesFromConnection(
					contactId, transportId, remote);
			// Create and run the incoming session
			createIncomingSession(ctx, reader).run();
			reader.dispose(false, true);
			// Interrupt the outgoing session so it finishes cleanly
			SyncSession out = outgoingSession;
			if (out != null) out.interrupt();
		} catch (DbException | IOException e) {
			logException(LOG, WARNING, e);
			onReadError();
		} finally {
			connectionRegistry.unregisterConnection(contactId, transportId,
					false);
		}
	}

	private void onReadError() {
		// 'Recognised' is always true for outgoing connections
		onReadError(true);
	}
}
