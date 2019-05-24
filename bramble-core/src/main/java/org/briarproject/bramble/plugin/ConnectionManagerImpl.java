package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.IoUtils.read;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class ConnectionManagerImpl implements ConnectionManager {

	private static final Logger LOG =
			getLogger(ConnectionManagerImpl.class.getName());

	private final Executor ioExecutor;
	private final KeyManager keyManager;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final SyncSessionFactory syncSessionFactory;
	private final ConnectionRegistry connectionRegistry;

	@Inject
	ConnectionManagerImpl(@IoExecutor Executor ioExecutor,
			KeyManager keyManager, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			ConnectionRegistry connectionRegistry) {
		this.ioExecutor = ioExecutor;
		this.keyManager = keyManager;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.syncSessionFactory = syncSessionFactory;
		this.connectionRegistry = connectionRegistry;
	}

	@Override
	public void manageIncomingConnection(TransportId t,
			TransportConnectionReader r) {
		ioExecutor.execute(new ManageIncomingSimplexConnection(t, r));
	}

	@Override
	public void manageIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new ManageIncomingDuplexConnection(t, d));
	}

	@Override
	public void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w) {
		ioExecutor.execute(new ManageOutgoingSimplexConnection(c, t, w));
	}

	@Override
	public void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new ManageOutgoingDuplexConnection(c, t, d));
	}

	private byte[] readTag(InputStream in) throws IOException {
		byte[] tag = new byte[TAG_LENGTH];
		read(in, tag);
		return tag;
	}

	private SyncSession createIncomingSession(StreamContext ctx,
			TransportConnectionReader r) throws IOException {
		InputStream streamReader = streamReaderFactory.createStreamReader(
				r.getInputStream(), ctx);
		ContactId c = requireNonNull(ctx.getContactId());
		return syncSessionFactory.createIncomingSession(c, streamReader);
	}

	private SyncSession createSimplexOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w) throws IOException {
		StreamWriter streamWriter = streamWriterFactory.createStreamWriter(
				w.getOutputStream(), ctx);
		ContactId c = requireNonNull(ctx.getContactId());
		return syncSessionFactory.createSimplexOutgoingSession(c,
				w.getMaxLatency(), streamWriter);
	}

	private SyncSession createDuplexOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w) throws IOException {
		StreamWriter streamWriter = streamWriterFactory.createStreamWriter(
				w.getOutputStream(), ctx);
		ContactId c = requireNonNull(ctx.getContactId());
		return syncSessionFactory.createDuplexOutgoingSession(c,
				w.getMaxLatency(), w.getMaxIdleTime(), streamWriter);
	}

	private void disposeOnError(TransportConnectionReader reader,
			boolean recognised) {
		try {
			reader.dispose(true, recognised);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void disposeOnError(TransportConnectionWriter writer) {
		try {
			writer.dispose(true);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	private class ManageIncomingSimplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;

		private ManageIncomingSimplexConnection(TransportId transportId,
				TransportConnectionReader reader) {
			this.transportId = transportId;
			this.reader = reader;
		}

		@Override
		public void run() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader.getInputStream());
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
				onError(false);
				return;
			}
			if (ctx == null) {
				LOG.info("Unrecognised tag");
				onError(false);
				return;
			}
			ContactId contactId = ctx.getContactId();
			if (contactId == null) {
				LOG.warning("Received rendezvous stream, expected contact");
				onError(true);
				return;
			}
			if (ctx.isHandshakeMode()) {
				// TODO: Support handshake mode for contacts
				LOG.warning("Received handshake tag, expected rotation mode");
				onError(true);
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId, true);
			try {
				// Create and run the incoming session
				createIncomingSession(ctx, reader).run();
				reader.dispose(false, true);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onError(true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId,
						true);
			}
		}

		private void onError(boolean recognised) {
			disposeOnError(reader, recognised);
		}
	}

	private class ManageOutgoingSimplexConnection implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionWriter writer;

		private ManageOutgoingSimplexConnection(ContactId contactId,
				TransportId transportId, TransportConnectionWriter writer) {
			this.contactId = contactId;
			this.transportId = transportId;
			this.writer = writer;
		}

		@Override
		public void run() {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onError();
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				onError();
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId,
					false);
			try {
				// Create and run the outgoing session
				createSimplexOutgoingSession(ctx, writer).run();
				writer.dispose(false);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onError();
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId,
						false);
			}
		}

		private void onError() {
			disposeOnError(writer);
		}
	}

	private class ManageIncomingDuplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		@Nullable
		private volatile SyncSession outgoingSession = null;

		private ManageIncomingDuplexConnection(TransportId transportId,
				DuplexTransportConnection connection) {
			this.transportId = transportId;
			reader = connection.getReader();
			writer = connection.getWriter();
		}

		@Override
		public void run() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader.getInputStream());
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
				onError(false);
				return;
			}
			if (ctx == null) {
				LOG.info("Unrecognised tag");
				onError(false);
				return;
			}
			ContactId contactId = ctx.getContactId();
			if (contactId == null) {
				LOG.warning("Expected contact tag, got rendezvous tag");
				onError(true);
				return;
			}
			if (ctx.isHandshakeMode()) {
				// TODO: Support handshake mode for contacts
				LOG.warning("Received handshake tag, expected rotation mode");
				onError(true);
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId, true);
			// Start the outgoing session on another thread
			ioExecutor.execute(() -> runOutgoingSession(contactId));
			try {
				// Create and run the incoming session
				createIncomingSession(ctx, reader).run();
				reader.dispose(false, true);
				// Interrupt the outgoing session so it finishes cleanly
				SyncSession out = outgoingSession;
				if (out != null) out.interrupt();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onError(true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId,
						true);
			}
		}

		private void runOutgoingSession(ContactId contactId) {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onError(true);
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				onError(true);
				return;
			}
			try {
				// Create and run the outgoing session
				SyncSession out = createDuplexOutgoingSession(ctx, writer);
				outgoingSession = out;
				out.run();
				writer.dispose(false);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onError(true);
			}
		}

		private void onError(boolean recognised) {
			disposeOnError(reader, recognised);
			disposeOnError(writer);
		}
	}

	private class ManageOutgoingDuplexConnection implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		@Nullable
		private volatile SyncSession outgoingSession = null;

		private ManageOutgoingDuplexConnection(ContactId contactId,
				TransportId transportId, DuplexTransportConnection connection) {
			this.contactId = contactId;
			this.transportId = transportId;
			reader = connection.getReader();
			writer = connection.getWriter();
		}

		@Override
		public void run() {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onError();
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				onError();
				return;
			}
			if (ctx.isHandshakeMode()) {
				// TODO: Support handshake mode for contacts
				LOG.warning("Cannot use handshake mode stream context");
				onError();
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
				onError();
			}
		}

		private void runIncomingSession() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader.getInputStream());
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
				onError();
				return;
			}
			// Unrecognised tags are suspicious in this case
			if (ctx == null) {
				LOG.warning("Unrecognised tag for returning stream");
				onError();
				return;
			}
			// Check that the stream comes from the expected contact
			ContactId inContactId = ctx.getContactId();
			if (inContactId == null) {
				LOG.warning("Expected contact tag, got rendezvous tag");
				onError();
				return;
			}
			if (!contactId.equals(inContactId)) {
				LOG.warning("Wrong contact ID for returning stream");
				onError();
				return;
			}
			if (ctx.isHandshakeMode()) {
				// TODO: Support handshake mode for contacts
				LOG.warning("Received handshake tag, expected rotation mode");
				onError();
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId,
					false);
			try {
				// Create and run the incoming session
				createIncomingSession(ctx, reader).run();
				reader.dispose(false, true);
				// Interrupt the outgoing session so it finishes cleanly
				SyncSession out = outgoingSession;
				if (out != null) out.interrupt();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onError();
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId,
						false);
			}
		}

		private void onError() {
			// 'Recognised' is always true for outgoing connections
			disposeOnError(reader, true);
			disposeOnError(writer);
		}
	}
}
