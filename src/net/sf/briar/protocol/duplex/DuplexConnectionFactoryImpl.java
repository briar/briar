package net.sf.briar.protocol.duplex;

import static java.util.logging.Level.WARNING;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.protocol.duplex.DuplexConnectionFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class DuplexConnectionFactoryImpl implements DuplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(DuplexConnectionFactoryImpl.class.getName());

	private final Executor dbExecutor, verificationExecutor;
	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final ProtocolReaderFactory protoReaderFactory;
	private final ProtocolWriterFactory protoWriterFactory;

	@Inject
	DuplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, KeyManager keyManager,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory, ProtocolWriterFactory protoWriterFactory) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.db = db;
		this.keyManager = keyManager;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
	}

	public void createIncomingConnection(ConnectionContext ctx,
			DuplexTransportConnection transport) {
		final DuplexConnection conn = new IncomingDuplexConnection(dbExecutor,
				verificationExecutor, db, connRegistry, connReaderFactory,
				connWriterFactory, protoReaderFactory, protoWriterFactory, ctx,
				transport);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write, "DuplexConnectionWriter").start();
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read, "DuplexConnectionReader").start();
	}

	public void createOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection transport) {
		ConnectionContext ctx = keyManager.getConnectionContext(c, t);
		if(ctx == null) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Could not create outgoing connection context");
			return;
		}
		final DuplexConnection conn = new OutgoingDuplexConnection(dbExecutor,
				verificationExecutor, db, connRegistry, connReaderFactory,
				connWriterFactory, protoReaderFactory, protoWriterFactory, ctx,
				transport);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write, "DuplexConnectionWriter").start();
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read, "DuplexConnectionReader").start();
	}
}
