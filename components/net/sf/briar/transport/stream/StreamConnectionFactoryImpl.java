package net.sf.briar.transport.stream;

import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamConnectionFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

import com.google.inject.Inject;

class StreamConnectionFactoryImpl implements StreamConnectionFactory {

	private final Executor executor;
	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoReaderFactory;
	private final ProtocolWriterFactory protoWriterFactory;

	@Inject
	StreamConnectionFactoryImpl(Executor executor,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory) {
		this.executor = executor;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.db = db;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
	}

	public void createIncomingConnection(ConnectionContext ctx,
			StreamTransportConnection s, byte[] tag) {
		final StreamConnection conn = new IncomingStreamConnection(executor,
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, ctx, s, tag);
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

	public void createOutgoingConnection(ContactId c, TransportIndex i,
			StreamTransportConnection s) {
		final StreamConnection conn = new OutgoingStreamConnection(executor,
				connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, c, i, s);
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
