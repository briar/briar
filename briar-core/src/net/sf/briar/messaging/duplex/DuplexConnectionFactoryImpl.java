package net.sf.briar.messaging.duplex;

import static java.util.logging.Level.WARNING;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.duplex.DuplexConnectionFactory;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class DuplexConnectionFactoryImpl implements DuplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(DuplexConnectionFactoryImpl.class.getName());

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	DuplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			KeyManager keyManager, ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.keyManager = keyManager;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
	}

	public void createIncomingConnection(ConnectionContext ctx,
			DuplexTransportConnection transport) {
		final DuplexConnection conn = new IncomingDuplexConnection(dbExecutor,
				cryptoExecutor, messageVerifier, db, connRegistry,
				connReaderFactory, connWriterFactory, packetReaderFactory,
				packetWriterFactory, ctx, transport);
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
				cryptoExecutor, messageVerifier, db, connRegistry,
				connReaderFactory, connWriterFactory, packetReaderFactory,
				packetWriterFactory, ctx, transport);
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
