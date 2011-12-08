package net.sf.briar.transport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.batch.BatchConnectionFactory;
import net.sf.briar.api.protocol.stream.StreamConnectionFactory;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRecogniserExecutor;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.api.transport.TransportConstants;

import com.google.inject.Inject;

class ConnectionDispatcherImpl implements ConnectionDispatcher {

	private static final Logger LOG =
		Logger.getLogger(ConnectionDispatcherImpl.class.getName());

	private final Executor executor;
	private final ConnectionRecogniser recogniser;
	private final BatchConnectionFactory batchConnFactory;
	private final StreamConnectionFactory streamConnFactory;

	@Inject
	ConnectionDispatcherImpl(@ConnectionRecogniserExecutor Executor executor,
			ConnectionRecogniser recogniser,
			BatchConnectionFactory batchConnFactory,
			StreamConnectionFactory streamConnFactory) {
		this.executor = executor;
		this.recogniser = recogniser;
		this.batchConnFactory = batchConnFactory;
		this.streamConnFactory = streamConnFactory;
	}

	public void dispatchReader(TransportId t, BatchTransportReader r) {
		executor.execute(new DispatchBatchConnection(t, r));
	}

	public void dispatchWriter(ContactId c, TransportIndex i,
			BatchTransportWriter w) {
		batchConnFactory.createOutgoingConnection(c, i, w);
	}

	public void dispatchIncomingConnection(TransportId t,
			StreamTransportConnection s) {
		executor.execute(new DispatchStreamConnection(t, s));
	}

	public void dispatchOutgoingConnection(ContactId c, TransportIndex i,
			StreamTransportConnection s) {
		streamConnFactory.createOutgoingConnection(c, i, s);
	}

	private byte[] readTag(InputStream in) throws IOException {
		byte[] b = new byte[TransportConstants.TAG_LENGTH];
		int offset = 0;
		while(offset < b.length) {
			int read = in.read(b, offset, b.length - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		return b;
	}

	private class DispatchBatchConnection implements Runnable {

		private final TransportId transportId;
		private final BatchTransportReader transport;

		private DispatchBatchConnection(TransportId transportId,
				BatchTransportReader transport) {
			this.transportId = transportId;
			this.transport = transport;
		}

		public void run() {
			try {
				byte[] tag = readTag(transport.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(transportId,
						tag);
				if(ctx == null) transport.dispose(false, false);
				else batchConnFactory.createIncomingConnection(ctx, transport,
						tag);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			}
		}
	}

	private class DispatchStreamConnection implements Runnable {

		private final TransportId transportId;
		private final StreamTransportConnection transport;

		private DispatchStreamConnection(TransportId transportId,
				StreamTransportConnection transport) {
			this.transportId = transportId;
			this.transport = transport;
		}

		public void run() {
			try {
				byte[] tag = readTag(transport.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(transportId,
						tag);
				if(ctx == null) transport.dispose(false, false);
				else streamConnFactory.createIncomingConnection(ctx, transport,
						tag);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			}
		}
	}
}