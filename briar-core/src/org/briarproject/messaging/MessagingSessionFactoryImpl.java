package org.briarproject.messaging;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.MessagingSessionFactory;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;

class MessagingSessionFactoryImpl implements MessagingSessionFactory {

	private final DatabaseComponent db;
	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final EventBus eventBus;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	MessagingSessionFactoryImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, EventBus eventBus,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.eventBus = eventBus;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
	}

	public MessagingSession createIncomingSession(ContactId c, TransportId t,
			InputStream in) {
		return new IncomingSession(db, dbExecutor, cryptoExecutor, eventBus,
				messageVerifier, packetReaderFactory, c, t, in);
	}

	public MessagingSession createOutgoingSession(ContactId c, TransportId t,
			long maxLatency, boolean duplex, OutputStream out) {
		if(duplex) return new DuplexOutgoingSession(db, dbExecutor, eventBus,
				packetWriterFactory, c, t, maxLatency, out);
		else return new SimplexOutgoingSession(db, dbExecutor, eventBus,
				packetWriterFactory, c, t, maxLatency, out);
	}
}
