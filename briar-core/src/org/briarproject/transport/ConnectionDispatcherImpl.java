package org.briarproject.transport;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.MessagingSessionFactory;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.transport.ConnectionDispatcher;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TagRecogniser;

class ConnectionDispatcherImpl implements ConnectionDispatcher {

	private static final Logger LOG =
			Logger.getLogger(ConnectionDispatcherImpl.class.getName());

	private final Executor ioExecutor;
	private final KeyManager keyManager;
	private final TagRecogniser tagRecogniser;
	private final MessagingSessionFactory messagingSessionFactory;
	private final ConnectionRegistry connectionRegistry;

	@Inject
	ConnectionDispatcherImpl(@IoExecutor Executor ioExecutor,
			KeyManager keyManager, TagRecogniser tagRecogniser,
			MessagingSessionFactory messagingSessionFactory,
			ConnectionRegistry connectionRegistry) {
		this.ioExecutor = ioExecutor;
		this.keyManager = keyManager;
		this.tagRecogniser = tagRecogniser;
		this.messagingSessionFactory = messagingSessionFactory;
		this.connectionRegistry = connectionRegistry;
	}

	public void dispatchIncomingConnection(TransportId t,
			TransportConnectionReader r) {
		ioExecutor.execute(new DispatchIncomingSimplexConnection(t, r));
	}

	public void dispatchIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new DispatchIncomingDuplexConnection(t, d));
	}

	public void dispatchOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w) {
		ioExecutor.execute(new DispatchOutgoingSimplexConnection(c, t, w));
	}

	public void dispatchOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(new DispatchOutgoingDuplexConnection(c, t, d));
	}

	private StreamContext readAndRecogniseTag(TransportId t,
			TransportConnectionReader r) {
		// Read the tag
		byte[] tag = new byte[TAG_LENGTH];
		try {
			InputStream in = r.getInputStream();
			int offset = 0;
			while(offset < tag.length) {
				int read = in.read(tag, offset, tag.length - offset);
				if(read == -1) throw new EOFException();
				offset += read;
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(r, true, false);
			return null;
		}
		// Recognise the tag
		StreamContext ctx = null;
		try {
			ctx = tagRecogniser.recogniseTag(t, tag);
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(r, true, false);
			return null;
		}
		if(ctx == null) dispose(r, false, false);
		return ctx;
	}

	private void runAndDispose(StreamContext ctx, TransportConnectionReader r) {
		MessagingSession in =
				messagingSessionFactory.createIncomingSession(ctx, r);
		ContactId contactId = ctx.getContactId();
		TransportId transportId = ctx.getTransportId();
		connectionRegistry.registerConnection(contactId, transportId);
		try {
			in.run();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(r, true, true);
			return;
		} finally {
			connectionRegistry.unregisterConnection(contactId, transportId);
		}
		dispose(r, false, true);
	}

	private void dispose(TransportConnectionReader r, boolean exception,
			boolean recognised) {
		try {
			r.dispose(exception, recognised);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void runAndDispose(StreamContext ctx, TransportConnectionWriter w,
			boolean duplex) {
		MessagingSession out =
				messagingSessionFactory.createOutgoingSession(ctx, w, duplex);
		ContactId contactId = ctx.getContactId();
		TransportId transportId = ctx.getTransportId();
		connectionRegistry.registerConnection(contactId, transportId);
		try {
			out.run();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(w, true);
			return;
		} finally {
			connectionRegistry.unregisterConnection(contactId, transportId);
		}
		dispose(w, false);
	}

	private void dispose(TransportConnectionWriter w, boolean exception) {
		try {
			w.dispose(exception);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class DispatchIncomingSimplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;

		private DispatchIncomingSimplexConnection(TransportId transportId,
				TransportConnectionReader reader) {
			this.transportId = transportId;
			this.reader = reader;
		}

		public void run() {
			// Read and recognise the tag
			StreamContext ctx = readAndRecogniseTag(transportId, reader);
			if(ctx == null) return;
			// Run the incoming session
			runAndDispose(ctx, reader);
		}
	}

	private class DispatchOutgoingSimplexConnection implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionWriter writer;

		private DispatchOutgoingSimplexConnection(ContactId contactId,
				TransportId transportId, TransportConnectionWriter writer) {
			this.contactId = contactId;
			this.transportId = transportId;
			this.writer = writer;
		}

		public void run() {
			// Allocate a stream context
			StreamContext ctx = keyManager.getStreamContext(contactId,
					transportId);
			if(ctx == null) {
				dispose(writer, false);
				return;
			}
			// Run the outgoing session
			runAndDispose(ctx, writer, false);
		}
	}

	private class DispatchIncomingDuplexConnection implements Runnable {

		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		private DispatchIncomingDuplexConnection(TransportId transportId,
				DuplexTransportConnection transport) {
			this.transportId = transportId;
			reader = transport.getReader();
			writer = transport.getWriter();
		}

		public void run() {
			// Read and recognise the tag
			StreamContext ctx = readAndRecogniseTag(transportId, reader);
			if(ctx == null) return;
			// Start the outgoing session on another thread
			ioExecutor.execute(new DispatchIncomingDuplexConnectionSide2(
					ctx.getContactId(), transportId, writer));
			// Run the incoming session
			runAndDispose(ctx, reader);
		}
	}

	private class DispatchIncomingDuplexConnectionSide2 implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionWriter writer;

		private DispatchIncomingDuplexConnectionSide2(ContactId contactId,
				TransportId transportId, TransportConnectionWriter writer) {
			this.contactId = contactId;
			this.transportId = transportId;
			this.writer = writer;
		}

		public void run() {
			// Allocate a stream context
			StreamContext ctx = keyManager.getStreamContext(contactId,
					transportId);
			if(ctx == null) {
				dispose(writer, false);
				return;
			}
			// Run the outgoing session
			runAndDispose(ctx, writer, true);
		}
	}

	private class DispatchOutgoingDuplexConnection implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionReader reader;
		private final TransportConnectionWriter writer;

		private DispatchOutgoingDuplexConnection(ContactId contactId,
				TransportId transportId, DuplexTransportConnection transport) {
			this.contactId = contactId;
			this.transportId = transportId;
			reader = transport.getReader();
			writer = transport.getWriter();
		}

		public void run() {
			// Allocate a stream context
			StreamContext ctx = keyManager.getStreamContext(contactId,
					transportId);
			if(ctx == null) {
				dispose(writer, false);
				return;
			}
			// Start the incoming session on another thread
			ioExecutor.execute(new DispatchOutgoingDuplexConnectionSide2(
					contactId, transportId, reader));
			// Run the outgoing session
			runAndDispose(ctx, writer, true);
		}
	}

	private class DispatchOutgoingDuplexConnectionSide2 implements Runnable {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportConnectionReader reader;

		private DispatchOutgoingDuplexConnectionSide2(ContactId contactId,
				TransportId transportId, TransportConnectionReader reader) {
			this.contactId = contactId;
			this.transportId = transportId;
			this.reader = reader;
		}

		public void run() {
			// Read and recognise the tag
			StreamContext ctx = readAndRecogniseTag(transportId, reader);
			if(ctx == null) return;
			// Check that the stream comes from the expected contact
			if(!ctx.getContactId().equals(contactId)) {
				LOG.warning("Wrong contact ID for duplex connection");
				dispose(reader, true, true);
				return;
			}
			// Run the incoming session
			runAndDispose(ctx, reader);
		}
	}
}