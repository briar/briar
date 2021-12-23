package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.api.sync.PriorityHandler;
import org.briarproject.bramble.api.sync.SyncRecordReader;
import org.briarproject.bramble.api.sync.SyncRecordReaderFactory;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.SyncRecordWriterFactory;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.sync.SyncSessionId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MINUTES;

@Immutable
@NotNullByDefault
class SyncSessionFactoryImpl implements SyncSessionFactory {

	private static final long ONE_MINUTE = MINUTES.toMillis(1);

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final CryptoComponent crypto;
	private final EventBus eventBus;
	private final Clock clock;
	private final SyncRecordReaderFactory recordReaderFactory;
	private final SyncRecordWriterFactory recordWriterFactory;

	@Inject
	SyncSessionFactoryImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			CryptoComponent crypto,
			EventBus eventBus,
			Clock clock,
			SyncRecordReaderFactory recordReaderFactory,
			SyncRecordWriterFactory recordWriterFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.crypto = crypto;
		this.eventBus = eventBus;
		this.clock = clock;
		this.recordReaderFactory = recordReaderFactory;
		this.recordWriterFactory = recordWriterFactory;
	}

	@Override
	public SyncSession createIncomingSession(ContactId c, InputStream in,
			PriorityHandler handler) {
		SyncRecordReader recordReader =
				recordReaderFactory.createRecordReader(in);
		return new IncomingSession(db, dbExecutor, eventBus, c, recordReader,
				handler);
	}

	@Override
	public SyncSession createSimplexOutgoingSession(ContactId c, TransportId t,
			long maxLatency, boolean eager, StreamWriter streamWriter) {
		// If we're not using eager retransmission and the max latency is more
		// than 1 minute, record the IDs of sent and acked messages so we can
		// resend them without a long delay if we crash mid-session
		SyncSessionId syncSessionId = null;
		if (!eager && maxLatency > ONE_MINUTE) {
			syncSessionId =
					new SyncSessionId(crypto.generateUniqueId().getBytes());
		}
		OutputStream out = streamWriter.getOutputStream();
		SyncRecordWriter recordWriter =
				recordWriterFactory.createRecordWriter(out);
		return new SimplexOutgoingSession(db, eventBus, c, t,
				maxLatency, eager, streamWriter, recordWriter, syncSessionId);
	}

	@Override
	public SyncSession createDuplexOutgoingSession(ContactId c, TransportId t,
			long maxLatency, int maxIdleTime, StreamWriter streamWriter,
			@Nullable Priority priority) {
		OutputStream out = streamWriter.getOutputStream();
		SyncRecordWriter recordWriter =
				recordWriterFactory.createRecordWriter(out);
		return new DuplexOutgoingSession(db, dbExecutor, eventBus, clock, c, t,
				maxLatency, maxIdleTime, streamWriter, recordWriter, priority);
	}
}
