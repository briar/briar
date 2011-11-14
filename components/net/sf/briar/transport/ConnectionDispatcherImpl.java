package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.BatchConnectionFactory;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.StreamConnectionFactory;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.api.transport.TransportConstants;

import com.google.inject.Inject;

public class ConnectionDispatcherImpl implements ConnectionDispatcher {

	private static final Logger LOG =
		Logger.getLogger(ConnectionDispatcherImpl.class.getName());

	private final ConnectionRecogniser recogniser;
	private final BatchConnectionFactory batchConnFactory;
	private final StreamConnectionFactory streamConnFactory;

	@Inject
	ConnectionDispatcherImpl(ConnectionRecogniser recogniser,
			BatchConnectionFactory batchConnFactory,
			StreamConnectionFactory streamConnFactory) {
		this.recogniser = recogniser;
		this.batchConnFactory = batchConnFactory;
		this.streamConnFactory = streamConnFactory;
	}

	public void dispatchReader(TransportId t, BatchTransportReader r) {
		// Read the encrypted IV
		byte[] encryptedIv;
		try {
			encryptedIv = readIv(r.getInputStream());
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			r.dispose(false);
			return;
		}
		// Get the connection context, or null if the IV wasn't expected
		ConnectionContext ctx;
		try {
			ctx = recogniser.acceptConnection(encryptedIv);
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			r.dispose(false);
			return;
		}
		if(ctx == null) {
			r.dispose(false);
			return;
		}
		if(!t.equals(ctx.getTransportId())) {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning("Connection has unexpected transport ID");
			r.dispose(false);
			return;
		}
		batchConnFactory.createIncomingConnection(ctx.getTransportIndex(),
				ctx.getContactId(), r, encryptedIv);
	}

	private byte[] readIv(InputStream in) throws IOException {
		byte[] b = new byte[TransportConstants.IV_LENGTH];
		int offset = 0;
		while(offset < b.length) {
			int read = in.read(b, offset, b.length - offset);
			if(read == -1) throw new IOException();
			offset += read;
		}
		return b;
	}

	public void dispatchWriter(TransportIndex i, ContactId c,
			BatchTransportWriter w) {
		batchConnFactory.createOutgoingConnection(i, c, w);
	}

	public void dispatchIncomingConnection(TransportId t,
			StreamTransportConnection s) {
		// Read the encrypted IV
		byte[] encryptedIv;
		try {
			encryptedIv = readIv(s.getInputStream());
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			s.dispose(false);
			return;
		}
		// Get the connection context, or null if the IV wasn't expected
		ConnectionContext ctx;
		try {
			ctx = recogniser.acceptConnection(encryptedIv);
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			s.dispose(false);
			return;
		}
		if(ctx == null) {
			s.dispose(false);
			return;
		}
		if(!t.equals(ctx.getTransportId())) {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning("Connection has unexpected transport ID");
			s.dispose(false);
			return;
		}
		streamConnFactory.createIncomingConnection(ctx.getTransportIndex(),
				ctx.getContactId(), s, encryptedIv);
	}

	public void dispatchOutgoingConnection(TransportIndex i, ContactId c,
			StreamTransportConnection s) {
		streamConnFactory.createOutgoingConnection(i, c, s);
	}
}
