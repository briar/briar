package org.briarproject.messaging.duplex;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.duplex.DuplexConnectionFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

class DuplexConnectionFactoryImpl implements DuplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(DuplexConnectionFactoryImpl.class.getName());

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final KeyManager keyManager;
	private final ConnectionRegistry connRegistry;
	private final StreamReaderFactory connReaderFactory;
	private final StreamWriterFactory connWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	DuplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			EventBus eventBus, KeyManager keyManager,
			ConnectionRegistry connRegistry,
			StreamReaderFactory connReaderFactory,
			StreamWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.eventBus = eventBus;
		this.keyManager = keyManager;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
	}

	public void createIncomingConnection(StreamContext ctx,
			DuplexTransportConnection transport) {
		final DuplexConnection conn = new IncomingDuplexConnection(dbExecutor,
				cryptoExecutor, messageVerifier, db, eventBus, connRegistry,
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
		StreamContext ctx = keyManager.getStreamContext(c, t);
		if(ctx == null) {
			LOG.warning("Could not create outgoing stream context");
			return;
		}
		final DuplexConnection conn = new OutgoingDuplexConnection(dbExecutor,
				cryptoExecutor, messageVerifier, db, eventBus, connRegistry,
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
