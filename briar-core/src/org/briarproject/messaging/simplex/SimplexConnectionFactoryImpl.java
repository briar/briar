package org.briarproject.messaging.simplex;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.simplex.SimplexConnectionFactory;
import org.briarproject.api.plugins.simplex.SimplexTransportReader;
import org.briarproject.api.plugins.simplex.SimplexTransportWriter;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

class SimplexConnectionFactoryImpl implements SimplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(SimplexConnectionFactoryImpl.class.getName());

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final ConnectionRegistry connRegistry;
	private final StreamReaderFactory connReaderFactory;
	private final StreamWriterFactory connWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	SimplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			KeyManager keyManager, ConnectionRegistry connRegistry,
			StreamReaderFactory connReaderFactory,
			StreamWriterFactory connWriterFactory,
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

	public void createIncomingConnection(StreamContext ctx,
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
		StreamContext ctx = keyManager.getStreamContext(c, t);
		if(ctx == null) {
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
