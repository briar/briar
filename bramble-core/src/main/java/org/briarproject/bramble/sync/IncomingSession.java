package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
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
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.api.sync.PriorityHandler;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncRecordReader;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.Versions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.util.LogUtils.logException;

/**
 * An incoming {@link SyncSession}.
 */
@ThreadSafe
@NotNullByDefault
class IncomingSession implements SyncSession, EventListener {

	private static final Logger LOG =
			getLogger(IncomingSession.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final SyncRecordReader recordReader;
	private final PriorityHandler priorityHandler;

	private volatile boolean interrupted = false;

	IncomingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId,
			SyncRecordReader recordReader, PriorityHandler priorityHandler) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.recordReader = recordReader;
		this.priorityHandler = priorityHandler;
	}

	@IoExecutor
	@Override
	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Read records until interrupted or EOF
			while (!interrupted) {
				if (recordReader.eof()) {
					LOG.info("End of stream");
					return;
				}
				if (recordReader.hasAck()) {
					Ack a = recordReader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if (recordReader.hasMessage()) {
					Message m = recordReader.readMessage();
					dbExecutor.execute(new ReceiveMessage(m));
				} else if (recordReader.hasOffer()) {
					Offer o = recordReader.readOffer();
					dbExecutor.execute(new ReceiveOffer(o));
				} else if (recordReader.hasRequest()) {
					Request r = recordReader.readRequest();
					dbExecutor.execute(new ReceiveRequest(r));
				} else if (recordReader.hasVersions()) {
					Versions v = recordReader.readVersions();
					dbExecutor.execute(new ReceiveVersions(v));
				} else if (recordReader.hasPriority()) {
					Priority p = recordReader.readPriority();
					priorityHandler.handle(p);
				} else {
					// unknown records are ignored in RecordReader#eof()
					throw new FormatException();
				}
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	@Override
	public void interrupt() {
		// FIXME: This won't interrupt a blocking read
		interrupted = true;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
		} else if (e instanceof LifecycleEvent) {
			LifecycleEvent l = (LifecycleEvent) e;
			if (l.getLifecycleState() == STOPPING) interrupt();
		}
	}

	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		@DatabaseExecutor
		@Override
		public void run() {
			try {
				db.transaction(false, txn ->
						db.receiveAck(txn, contactId, ack));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class ReceiveMessage implements Runnable {

		private final Message message;

		private ReceiveMessage(Message message) {
			this.message = message;
		}

		@DatabaseExecutor
		@Override
		public void run() {
			try {
				db.transaction(false, txn ->
						db.receiveMessage(txn, contactId, message));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class ReceiveOffer implements Runnable {

		private final Offer offer;

		private ReceiveOffer(Offer offer) {
			this.offer = offer;
		}

		@DatabaseExecutor
		@Override
		public void run() {
			try {
				db.transaction(false, txn ->
						db.receiveOffer(txn, contactId, offer));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class ReceiveRequest implements Runnable {

		private final Request request;

		private ReceiveRequest(Request request) {
			this.request = request;
		}

		@DatabaseExecutor
		@Override
		public void run() {
			try {
				db.transaction(false, txn ->
						db.receiveRequest(txn, contactId, request));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}

	private class ReceiveVersions implements Runnable {

		private final Versions versions;

		private ReceiveVersions(Versions versions) {
			this.versions = versions;
		}

		@DatabaseExecutor
		@Override
		public void run() {
			try {
				List<Byte> supported = versions.getSupportedVersions();
				db.transaction(false,
						txn -> db.setSyncVersions(txn, contactId, supported));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				interrupt();
			}
		}
	}
}
