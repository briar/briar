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

		private final TransportId t;
		private final BatchTransportReader r;

		private DispatchBatchConnection(TransportId t, BatchTransportReader r) {
			this.t = t;
			this.r = r;
		}

		public void run() {
			try {
				byte[] tag = readTag(r.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(t, tag);
				if(ctx == null) r.dispose(true);
				else batchConnFactory.createIncomingConnection(ctx, r, tag);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				r.dispose(false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				r.dispose(false);
			}
		}
	}

	private class DispatchStreamConnection implements Runnable {

		private final TransportId t;
		private final StreamTransportConnection s;

		private DispatchStreamConnection(TransportId t,
				StreamTransportConnection s) {
			this.t = t;
			this.s = s;
		}

		public void run() {
			try {
				byte[] tag = readTag(s.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(t, tag);
				if(ctx == null) s.dispose(true);
				else streamConnFactory.createIncomingConnection(ctx, s, tag);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				s.dispose(false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				s.dispose(false);
			}
		}
	}
}