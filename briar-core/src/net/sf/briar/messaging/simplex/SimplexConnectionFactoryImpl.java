package net.sf.briar.messaging.simplex;

import static java.util.logging.Level.WARNING;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.simplex.SimplexConnectionFactory;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class SimplexConnectionFactoryImpl implements SimplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(SimplexConnectionFactoryImpl.class.getName());

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
	SimplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
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
			SimplexTransportReader r) {
		final IncomingSimplexConnection conn = new IncomingSimplexConnection(
				dbExecutor, cryptoExecutor, messageVerifier, db, connRegistry,
				connReaderFactory, packetReaderFactory, ctx, r);
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read, "SimplexConnectionReader").start();
	}

	public void createOutgoingConnection(ContactId c, TransportId t,
			SimplexTransportWriter w) {
		ConnectionContext ctx = keyManager.getConnectionContext(c, t);
		if(ctx == null) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Could not create outgoing connection context");
			return;
		}		
		final OutgoingSimplexConnection conn = new OutgoingSimplexConnection(db,
				connRegistry, connWriterFactory, packetWriterFactory, ctx, w);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write, "SimplexConnectionWriter").start();
	}
}
