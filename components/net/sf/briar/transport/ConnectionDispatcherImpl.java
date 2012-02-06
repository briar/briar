package net.sf.briar.transport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.duplex.DuplexConnectionFactory;
import net.sf.briar.api.protocol.simplex.SimplexConnectionFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.IncomingConnectionExecutor;
import net.sf.briar.api.transport.TransportConstants;

import com.google.inject.Inject;

class ConnectionDispatcherImpl implements ConnectionDispatcher {

	private static final Logger LOG =
		Logger.getLogger(ConnectionDispatcherImpl.class.getName());

	private final Executor connExecutor;
	private final ConnectionRecogniser recogniser;
	private final SimplexConnectionFactory batchConnFactory;
	private final DuplexConnectionFactory streamConnFactory;

	@Inject
	ConnectionDispatcherImpl(@IncomingConnectionExecutor Executor connExecutor,
			ConnectionRecogniser recogniser,
			SimplexConnectionFactory batchConnFactory,
			DuplexConnectionFactory streamConnFactory) {
		this.connExecutor = connExecutor;
		this.recogniser = recogniser;
		this.batchConnFactory = batchConnFactory;
		this.streamConnFactory = streamConnFactory;
	}

	public void dispatchReader(TransportId t, SimplexTransportReader r) {
		connExecutor.execute(new DispatchSimplexConnection(t, r));
	}

	public void dispatchWriter(ContactId c, TransportId t, TransportIndex i,
			SimplexTransportWriter w) {
		batchConnFactory.createOutgoingConnection(c, t, i, w);
	}

	public void dispatchIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		connExecutor.execute(new DispatchDuplexConnection(t, d));
	}

	public void dispatchOutgoingConnection(ContactId c, TransportId t,
			TransportIndex i, DuplexTransportConnection d) {
		streamConnFactory.createOutgoingConnection(c, t, i, d);
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

	private class DispatchSimplexConnection implements Runnable {

		private final TransportId transportId;
		private final SimplexTransportReader transport;

		private DispatchSimplexConnection(TransportId transportId,
				SimplexTransportReader transport) {
			this.transportId = transportId;
			this.transport = transport;
		}

		public void run() {
			try {
				byte[] tag = readTag(transport.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(transportId,
						tag);
				if(ctx == null) transport.dispose(false, false);
				else batchConnFactory.createIncomingConnection(ctx, transportId,
						transport);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				transport.dispose(true, false);
			}
		}
	}

	private class DispatchDuplexConnection implements Runnable {

		private final TransportId transportId;
		private final DuplexTransportConnection transport;

		private DispatchDuplexConnection(TransportId transportId,
				DuplexTransportConnection transport) {
			this.transportId = transportId;
			this.transport = transport;
		}

		public void run() {
			try {
				byte[] tag = readTag(transport.getInputStream());
				ConnectionContext ctx = recogniser.acceptConnection(transportId,
						tag);
				if(ctx == null) transport.dispose(false, false);
				else streamConnFactory.createIncomingConnection(ctx,
						transportId, transport);
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