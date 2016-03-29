package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

class ConnectionManagerImpl implements ConnectionManager {

	private static final Logger LOG =
			Logger.getLogger(ConnectionManagerImpl.class.getName());

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

	public void manageIncomingConnection(TransportId t,
			TransportConnectionReader r) {
		ioExecutor.execute(new ManageIncomingSimplexConnection(t, r));
	}

	public void manageIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new ManageIncomingDuplexConnection(t, d));
	}

	public void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w) {
		ioExecutor.execute(new ManageOutgoingSimplexConnection(c, t, w));
	}

	public void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new ManageOutgoingDuplexConnection(c, t, d));
	}

	private byte[] readTag(TransportConnectionReader r) throws IOException {
		// Read the tag
		byte[] tag = new byte[TAG_LENGTH];
		InputStream in = r.getInputStream();
		int offset = 0;
		while (offset < tag.length) {
			int read = in.read(tag, offset, tag.length - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
		return tag;
	}

	private SyncSession createIncomingSession(StreamContext ctx,
			TransportConnectionReader r) throws IOException {
		InputStream streamReader = streamReaderFactory.createStreamReader(
				r.getInputStream(), ctx);
		return syncSessionFactory.createIncomingSession(ctx.getContactId(),
				streamReader);
	}

	private SyncSession createSimplexOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w) throws IOException {
		OutputStream streamWriter = streamWriterFactory.createStreamWriter(
				w.getOutputStream(), ctx);
		return syncSessionFactory.createSimplexOutgoingSession(
				ctx.getContactId(), w.getMaxLatency(), streamWriter);
	}

	private SyncSession createDuplexOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w) throws IOException {
		OutputStream streamWriter = streamWriterFactory.createStreamWriter(
				w.getOutputStream(), ctx);
		return syncSessionFactory.createDuplexOutgoingSession(
				ctx.getContactId(), w.getMaxLatency(), w.getMaxIdleTime(),
				streamWriter);
	}

	private class ManageIncomingSimplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;

		private ManageIncomingSimplexConnection(TransportId transportId,
				TransportConnectionReader reader) {
			this.transportId = transportId;
			this.reader = reader;
		}

		public void run() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader);
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, false);
				return;
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, false);
				return;
			}
			if (ctx == null) {
				LOG.info("Unrecognised tag");
				disposeReader(false, false);
				return;
			}
			ContactId contactId = ctx.getContactId();
			connectionRegistry.registerConnection(contactId, transportId);
			try {
				// Create and run the incoming session
				createIncomingSession(ctx, reader).run();
				disposeReader(false, true);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId);
			}
		}

		private void disposeReader(boolean exception, boolean recognised) {
			try {
				reader.dispose(exception, recognised);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
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

		public void run() {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				disposeWriter(false);
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId);
			try {
				// Create and run the outgoing session
				createSimplexOutgoingSession(ctx, writer).run();
				disposeWriter(false);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId);
			}
		}

		private void disposeWriter(boolean exception) {
			try {
				writer.dispose(exception);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ManageIncomingDuplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		private volatile ContactId contactId = null;
		private volatile SyncSession incomingSession = null;
		private volatile SyncSession outgoingSession = null;

		private ManageIncomingDuplexConnection(TransportId transportId,
				DuplexTransportConnection transport) {
			this.transportId = transportId;
			reader = transport.getReader();
			writer = transport.getWriter();
		}

		public void run() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader);
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, false);
				return;
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, false);
				return;
			}
			if (ctx == null) {
				LOG.info("Unrecognised tag");
				disposeReader(false, false);
				return;
			}
			contactId = ctx.getContactId();
			connectionRegistry.registerConnection(contactId, transportId);
			// Start the outgoing session on another thread
			ioExecutor.execute(new Runnable() {
				public void run() {
					runOutgoingSession();
				}
			});
			try {
				// Create and run the incoming session
				incomingSession = createIncomingSession(ctx, reader);
				incomingSession.run();
				disposeReader(false, true);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId);
			}
		}

		private void runOutgoingSession() {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				disposeWriter(false);
				return;
			}
			try {
				// Create and run the outgoing session
				outgoingSession = createDuplexOutgoingSession(ctx, writer);
				outgoingSession.run();
				disposeWriter(false);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
			}
		}

		private void disposeReader(boolean exception, boolean recognised) {
			if (exception && outgoingSession != null)
				outgoingSession.interrupt();
			try {
				reader.dispose(exception, recognised);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		private void disposeWriter(boolean exception) {
			if (exception && incomingSession != null)
				incomingSession.interrupt();
			try {
				writer.dispose(exception);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ManageOutgoingDuplexConnection implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		private volatile SyncSession incomingSession = null;
		private volatile SyncSession outgoingSession = null;

		private ManageOutgoingDuplexConnection(ContactId contactId,
				TransportId transportId, DuplexTransportConnection transport) {
			this.contactId = contactId;
			this.transportId = transportId;
			reader = transport.getReader();
			writer = transport.getWriter();
		}

		public void run() {
			// Allocate a stream context
			StreamContext ctx;
			try {
				ctx = keyManager.getStreamContext(contactId, transportId);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
				return;
			}
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				disposeWriter(false);
				return;
			}
			connectionRegistry.registerConnection(contactId, transportId);
			// Start the incoming session on another thread
			ioExecutor.execute(new Runnable() {
				public void run() {
					runIncomingSession();
				}
			});
			try {
				// Create and run the outgoing session
				outgoingSession = createDuplexOutgoingSession(ctx, writer);
				outgoingSession.run();
				disposeWriter(false);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeWriter(true);
			} finally {
				connectionRegistry.unregisterConnection(contactId, transportId);
			}
		}

		private void runIncomingSession() {
			// Read and recognise the tag
			StreamContext ctx;
			try {
				byte[] tag = readTag(reader);
				ctx = keyManager.getStreamContext(transportId, tag);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, true);
				return;
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, true);
				return;
			}
			// Unrecognised tags are suspicious in this case
			if (ctx == null) {
				LOG.warning("Unrecognised tag for returning stream");
				disposeReader(true, true);
				return;
			}
			// Check that the stream comes from the expected contact
			if (!ctx.getContactId().equals(contactId)) {
				LOG.warning("Wrong contact ID for returning stream");
				disposeReader(true, true);
				return;
			}
			try {
				// Create and run the incoming session
				incomingSession = createIncomingSession(ctx, reader);
				incomingSession.run();
				disposeReader(false, true);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				disposeReader(true, true);
			}
		}

		private void disposeReader(boolean exception, boolean recognised) {
			if (exception && outgoingSession != null)
				outgoingSession.interrupt();
			try {
				reader.dispose(exception, recognised);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		private void disposeWriter(boolean exception) {
			if (exception && incomingSession != null)
				incomingSession.interrupt();
			try {
				writer.dispose(exception);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
