package net.sf.briar.transport.stream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.api.transport.stream.StreamConnectionFactory;

import com.google.inject.Inject;

public class StreamConnectionFactoryImpl implements StreamConnectionFactory {

	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoReaderFactory;
	private final ProtocolWriterFactory protoWriterFactory;

	@Inject
	StreamConnectionFactoryImpl(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory) {
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.db = db;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
	}

	public Runnable[] createIncomingConnection(ContactId c,
			StreamTransportConnection s, byte[] encryptedIv) {
		final StreamConnection conn = new IncomingStreamConnection(
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, c, s, encryptedIv);
		Runnable[] runnables = new Runnable[2];
		runnables[0] = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		runnables[1] = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		return runnables;
	}

	public Runnable[] createOutgoingConnection(TransportId t, ContactId c,
			StreamTransportConnection s) {
		final StreamConnection conn = new OutgoingStreamConnection(
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, c, s, t);
		Runnable[] runnables = new Runnable[2];
		runnables[0] = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		runnables[1] = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		return runnables;
	}

}
