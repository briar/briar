package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

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
import net.sf.briar.api.protocol.duplex.DuplexConnectionFactory;
import net.sf.briar.api.protocol.simplex.SimplexConnectionFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.IncomingConnectionExecutor;

import com.google.inject.Inject;

class ConnectionDispatcherImpl implements ConnectionDispatcher {

	private static final Logger LOG =
			Logger.getLogger(ConnectionDispatcherImpl.class.getName());

	private final Executor connExecutor;
	private final ConnectionRecogniser recogniser;
	private final SimplexConnectionFactory simplexConnFactory;
	private final DuplexConnectionFactory duplexConnFactory;

	@Inject
	ConnectionDispatcherImpl(@IncomingConnectionExecutor Executor connExecutor,
			ConnectionRecogniser recogniser,
			SimplexConnectionFactory simplexConnFactory,
			DuplexConnectionFactory duplexConnFactory) {
		this.connExecutor = connExecutor;
		this.recogniser = recogniser;
		this.simplexConnFactory = simplexConnFactory;
		this.duplexConnFactory = duplexConnFactory;
	}

	public void dispatchReader(TransportId t, SimplexTransportReader r) {
		connExecutor.execute(new DispatchSimplexConnection(t, r));
	}

	public void dispatchWriter(ContactId c, TransportId t,
			SimplexTransportWriter w) {
		simplexConnFactory.createOutgoingConnection(c, t, w);
	}

	public void dispatchIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		connExecutor.execute(new DispatchDuplexConnection(t, d));
	}

	public void dispatchOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d) {
		duplexConnFactory.createOutgoingConnection(c, t, d);
	}

	private byte[] readTag(InputStream in) throws IOException {
		byte[] b = new byte[TAG_LENGTH];
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
				if(ctx == null) {
					transport.dispose(false, false);
				} else {
					simplexConnFactory.createIncomingConnection(ctx,
							transport);
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				try {
					transport.dispose(true, false);
				} catch(IOException e1) {
					if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				}
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				try {
					transport.dispose(true, false);
				} catch(IOException e1) {
					if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				}
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
				if(ctx == null) {
					transport.dispose(false, false);
				} else {
					duplexConnFactory.createIncomingConnection(ctx, transport);
				}
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