package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.RecordReader;
import org.briarproject.bramble.api.sync.RecordReaderFactory;
import org.briarproject.bramble.api.sync.RecordWriter;
import org.briarproject.bramble.api.sync.RecordWriterFactory;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.system.Clock;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class SyncSessionFactoryImpl implements SyncSessionFactory {

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Clock clock;
	private final RecordReaderFactory recordReaderFactory;
	private final RecordWriterFactory recordWriterFactory;

	@Inject
	SyncSessionFactoryImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, EventBus eventBus,
			Clock clock, RecordReaderFactory recordReaderFactory,
			RecordWriterFactory recordWriterFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.clock = clock;
		this.recordReaderFactory = recordReaderFactory;
		this.recordWriterFactory = recordWriterFactory;
	}

	@Override
	public SyncSession createIncomingSession(ContactId c, InputStream in) {
		RecordReader recordReader = recordReaderFactory.createRecordReader(in);
		return new IncomingSession(db, dbExecutor, eventBus, c, recordReader);
	}

	@Override
	public SyncSession createSimplexOutgoingSession(ContactId c,
			int maxLatency, OutputStream out) {
		RecordWriter recordWriter = recordWriterFactory.createRecordWriter(out);
		return new SimplexOutgoingSession(db, dbExecutor, eventBus, c,
				maxLatency, recordWriter);
	}

	@Override
	public SyncSession createDuplexOutgoingSession(ContactId c, int maxLatency,
			int maxIdleTime, OutputStream out) {
		RecordWriter recordWriter = recordWriterFactory.createRecordWriter(out);
		return new DuplexOutgoingSession(db, dbExecutor, eventBus, clock, c,
				maxLatency, maxIdleTime, recordWriter);
	}
}
