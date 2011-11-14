package net.sf.briar.transport.stream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamConnectionFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

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

	public void createIncomingConnection(TransportIndex i, ContactId c,
			StreamTransportConnection s, byte[] encryptedIv) {
		final StreamConnection conn = new IncomingStreamConnection(
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, i, c, s, encryptedIv);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write).start();
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read).start();
	}

	public void createOutgoingConnection(TransportIndex i, ContactId c,
			StreamTransportConnection s) {
		final StreamConnection conn = new OutgoingStreamConnection(
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, i, c, s);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write).start();
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read).start();
	}

}
