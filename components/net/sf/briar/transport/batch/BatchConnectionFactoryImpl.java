package net.sf.briar.transport.batch;

import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.BatchConnectionFactory;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class BatchConnectionFactoryImpl implements BatchConnectionFactory {

	private final Executor executor;
	private final DatabaseComponent db;
	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final ProtocolReaderFactory protoReaderFactory;
	private final ProtocolWriterFactory protoWriterFactory;

	@Inject
	BatchConnectionFactoryImpl(Executor executor, DatabaseComponent db,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory) {
		this.executor = executor;
		this.db = db;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
	}

	public void createIncomingConnection(ConnectionContext ctx,
			BatchTransportReader r, byte[] tag) {
		final IncomingBatchConnection conn = new IncomingBatchConnection(
				executor, db, connReaderFactory, protoReaderFactory, ctx, r,
				tag);
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read).start();
	}

	public void createOutgoingConnection(ContactId c, TransportIndex i,
			BatchTransportWriter w) {
		final OutgoingBatchConnection conn = new OutgoingBatchConnection(db,
				connWriterFactory, protoWriterFactory, c, i, w);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write).start();
	}
}
