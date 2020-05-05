package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.sync.event.GroupVisibilityUpdatedEvent;
import org.briarproject.bramble.api.sync.event.MessageRequestedEvent;
import org.briarproject.bramble.api.sync.event.MessageSharedEvent;
import org.briarproject.bramble.api.sync.event.MessageToAckEvent;
import org.briarproject.bramble.api.sync.event.MessageToRequestEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.SUPPORTED_VERSIONS;
import static org.briarproject.bramble.util.LogUtils.logException;

/**
 * An outgoing {@link SyncSession} suitable for duplex transports. The session
 * offers messages before sending them, keeps its output stream open when there
 * are no records to send, and reacts to events that make records available to
 * send.
 */
@ThreadSafe
@NotNullByDefault
class DuplexOutgoingSession implements SyncSession, EventListener {

	private static final Logger LOG =
			getLogger(DuplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE = () -> {
	};
	private static final ThrowingRunnable<IOException>
			NEXT_SEND_TIME_DECREASED = () -> {
	};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Clock clock;
	private final ContactId contactId;
	private final TransportId transportId;
	private final int maxLatency, maxIdleTime;
	private final StreamWriter streamWriter;
	private final SyncRecordWriter recordWriter;
	@Nullable
	private final Priority priority;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private final AtomicBoolean generateAckQueued = new AtomicBoolean(false);
	private final AtomicBoolean generateBatchQueued = new AtomicBoolean(false);
	private final AtomicBoolean generateOfferQueued = new AtomicBoolean(false);
	private final AtomicBoolean generateRequestQueued =
			new AtomicBoolean(false);
	private final AtomicLong nextSendTime = new AtomicLong(Long.MAX_VALUE);

	private volatile boolean interrupted = false;

	DuplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, Clock clock, ContactId contactId,
			TransportId transportId, int maxLatency, int maxIdleTime,
			StreamWriter streamWriter, SyncRecordWriter recordWriter,
			@Nullable Priority priority) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.clock = clock;
		this.contactId = contactId;
		this.transportId = transportId;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		this.streamWriter = streamWriter;
		this.recordWriter = recordWriter;
		this.priority = priority;
		writerTasks = new LinkedBlockingQueue<>();
	}

	@IoExecutor
	@Override
	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Send our supported protocol versions
			recordWriter.writeVersions(new Versions(SUPPORTED_VERSIONS));
			// Send our connection priority, if this is an outgoing connection
			if (priority != null) recordWriter.writePriority(priority);
			// Start a query for each type of record
			generateAck();
			generateBatch();
			generateOffer();
			generateRequest();
			long now = clock.currentTimeMillis();
			long nextKeepalive = now + maxIdleTime;
			boolean dataToFlush = true;
			// Write records until interrupted
			try {
				while (!interrupted) {
					// Work out how long we should wait for a record
					now = clock.currentTimeMillis();
					long keepaliveWait = Math.max(0, nextKeepalive - now);
					long sendWait = Math.max(0, nextSendTime.get() - now);
					long wait = Math.min(keepaliveWait, sendWait);
					// Flush any unflushed data if we're going to wait
					if (wait > 0 && dataToFlush && writerTasks.isEmpty()) {
						recordWriter.flush();
						dataToFlush = false;
						nextKeepalive = now + maxIdleTime;
					}
					// Wait for a record
					ThrowingRunnable<IOException> task = writerTasks.poll(wait,
							MILLISECONDS);
					if (task == null) {
						now = clock.currentTimeMillis();
						if (now >= nextSendTime.get()) {
							// Check for retransmittable messages
							LOG.info("Checking for retransmittable messages");
							setNextSendTime(Long.MAX_VALUE);
							generateBatch();
							generateOffer();
						}
						if (now >= nextKeepalive) {
							// Flush the stream to keep it alive
							LOG.info("Sending keepalive");
							recordWriter.flush();
							dataToFlush = false;
							nextKeepalive = now + maxIdleTime;
						}
					} else if (task == CLOSE) {
						LOG.info("Closed");
						break;
					} else if (task == NEXT_SEND_TIME_DECREASED) {
						LOG.info("Next send time decreased");
					} else {
						task.run();
						dataToFlush = true;
					}
				}
				streamWriter.sendEndOfStream();
			} catch (InterruptedException e) {
				LOG.info("Interrupted while waiting for a record to write");
				Thread.currentThread().interrupt();
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	private void generateAck() {
		if (generateAckQueued.compareAndSet(false, true))
			dbExecutor.execute(new GenerateAck());
	}

	private void generateBatch() {
		if (generateBatchQueued.compareAndSet(false, true))
			dbExecutor.execute(new GenerateBatch());
	}

	private void generateOffer() {
		if (generateOfferQueued.compareAndSet(false, true))
			dbExecutor.execute(new GenerateOffer());
	}

	private void generateRequest() {
		if (generateRequestQueued.compareAndSet(false, true))
			dbExecutor.execute(new GenerateRequest());
	}

	private void setNextSendTime(long time) {
		long old = nextSendTime.getAndSet(time);
		if (time < old) writerTasks.add(NEXT_SEND_TIME_DECREASED);
	}

	@Override
	public void interrupt() {
		interrupted = true;
		writerTasks.add(CLOSE);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
		} else if (e instanceof MessageSharedEvent) {
			generateOffer();
		} else if (e instanceof GroupVisibilityUpdatedEvent) {
			GroupVisibilityUpdatedEvent g = (GroupVisibilityUpdatedEvent) e;
			if (g.getAffectedContacts().contains(contactId))
				generateOffer();
		} else if (e instanceof MessageRequestedEvent) {
			if (((MessageRequestedEvent) e).getContactId().equals(contactId))
				generateBatch();
		} else if (e instanceof MessageToAckEvent) {
			if (((MessageToAckEvent) e).getContactId().equals(contactId))
				generateAck();
		} else if (e instanceof MessageToRequestEvent) {
			if (((MessageToRequestEvent) e).getContactId().equals(contactId))
				generateRequest();
		} else if (e instanceof LifecycleEvent) {
			LifecycleEvent l = (LifecycleEvent) e;
			if (l.getLifecycleState() == STOPPING) interrupt();
		} else if (e instanceof CloseSyncConnectionsEvent) {
			CloseSyncConnectionsEvent c = (CloseSyncConnectionsEvent) e;
			if (c.getTransportId().equals(transportId)) interrupt();
		}
	}

	private class GenerateAck implements Runnable {

		@DatabaseExecutor
		@Override
		public void run() {
			if (interrupted) return;
			if (!generateAckQueued.getAndSet(false)) throw new AssertionError();
			try {
				Ack a = db.transactionWithNullableResult(false, txn ->
						db.generateAck(txn, contactId, MAX_MESSAGE_IDS));
				if (LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
				if (a != null) writerTasks.add(new WriteAck(a));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class WriteAck implements ThrowingRunnable<IOException> {

		private final Ack ack;

		private WriteAck(Ack ack) {
			this.ack = ack;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeAck(ack);
			LOG.info("Sent ack");
			generateAck();
		}
	}

	private class GenerateBatch implements Runnable {

		@DatabaseExecutor
		@Override
		public void run() {
			if (interrupted) return;
			if (!generateBatchQueued.getAndSet(false))
				throw new AssertionError();
			try {
				Collection<Message> b =
						db.transactionWithNullableResult(false, txn -> {
							Collection<Message> batch =
									db.generateRequestedBatch(txn, contactId,
											MAX_RECORD_PAYLOAD_BYTES,
											maxLatency);
							setNextSendTime(db.getNextSendTime(txn, contactId));
							return batch;
						});
				if (LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if (b != null) writerTasks.add(new WriteBatch(b));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class WriteBatch implements ThrowingRunnable<IOException> {

		private final Collection<Message> batch;

		private WriteBatch(Collection<Message> batch) {
			this.batch = batch;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			for (Message m : batch) recordWriter.writeMessage(m);
			LOG.info("Sent batch");
			generateBatch();
		}
	}

	private class GenerateOffer implements Runnable {

		@DatabaseExecutor
		@Override
		public void run() {
			if (interrupted) return;
			if (!generateOfferQueued.getAndSet(false))
				throw new AssertionError();
			try {
				Offer o = db.transactionWithNullableResult(false, txn -> {
					Offer offer = db.generateOffer(txn, contactId,
							MAX_MESSAGE_IDS, maxLatency);
					setNextSendTime(db.getNextSendTime(txn, contactId));
					return offer;
				});
				if (LOG.isLoggable(INFO))
					LOG.info("Generated offer: " + (o != null));
				if (o != null) writerTasks.add(new WriteOffer(o));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class WriteOffer implements ThrowingRunnable<IOException> {

		private final Offer offer;

		private WriteOffer(Offer offer) {
			this.offer = offer;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeOffer(offer);
			LOG.info("Sent offer");
			generateOffer();
		}
	}

	private class GenerateRequest implements Runnable {

		@DatabaseExecutor
		@Override
		public void run() {
			if (interrupted) return;
			if (!generateRequestQueued.getAndSet(false))
				throw new AssertionError();
			try {
				Request r = db.transactionWithNullableResult(false, txn ->
						db.generateRequest(txn, contactId, MAX_MESSAGE_IDS));
				if (LOG.isLoggable(INFO))
					LOG.info("Generated request: " + (r != null));
				if (r != null) writerTasks.add(new WriteRequest(r));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class WriteRequest implements ThrowingRunnable<IOException> {

		private final Request request;

		private WriteRequest(Request request) {
			this.request = request;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeRequest(request);
			LOG.info("Sent request");
			generateRequest();
		}
	}
}
