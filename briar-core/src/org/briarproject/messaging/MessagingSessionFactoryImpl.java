package org.briarproject.messaging;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.MessagingSessionFactory;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

class MessagingSessionFactoryImpl implements MessagingSessionFactory {

	private final DatabaseComponent db;
	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final EventBus eventBus;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	MessagingSessionFactoryImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, EventBus eventBus,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.eventBus = eventBus;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
	}

	public MessagingSession createIncomingSession(StreamContext ctx,
			TransportConnectionReader r) {
		return new IncomingSession(db, dbExecutor, cryptoExecutor,
				messageVerifier, streamReaderFactory, packetReaderFactory,
				ctx, r);
	}

	public MessagingSession createOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w, boolean duplex) {
		if(duplex) return new ReactiveOutgoingSession(db, dbExecutor, eventBus,
				streamWriterFactory, packetWriterFactory, ctx, w);
		else return new SinglePassOutgoingSession(db, dbExecutor,
				streamWriterFactory, packetWriterFactory, ctx, w);
	}
}
