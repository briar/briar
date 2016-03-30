package org.briarproject.sync;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Clock;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import javax.inject.Inject;

class SyncSessionFactoryImpl implements SyncSessionFactory {

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Clock clock;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;

	@Inject
	SyncSessionFactoryImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, EventBus eventBus,
			Clock clock, PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.clock = clock;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
	}

	public SyncSession createIncomingSession(ContactId c, InputStream in) {
		PacketReader packetReader = packetReaderFactory.createPacketReader(in);
		return new IncomingSession(db, dbExecutor, eventBus, c, packetReader);
	}

	public SyncSession createSimplexOutgoingSession(ContactId c,
			int maxLatency, OutputStream out) {
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(out);
		return new SimplexOutgoingSession(db, dbExecutor, eventBus, c,
				maxLatency, packetWriter);
	}

	public SyncSession createDuplexOutgoingSession(ContactId c, int maxLatency,
			int maxIdleTime, OutputStream out) {
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(out);
		return new DuplexOutgoingSession(db, dbExecutor, eventBus, clock, c,
				maxLatency, maxIdleTime, packetWriter);
	}
}
