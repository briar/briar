package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SyncSession;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * An incoming {@link org.briarproject.api.sync.SyncSession SyncSession}.
 */
class IncomingSession implements SyncSession, EventListener {

	private static final Logger LOG =
			Logger.getLogger(IncomingSession.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final PacketReader packetReader;

	private volatile boolean interrupted = false;

	IncomingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId,
			PacketReader packetReader) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.packetReader = packetReader;
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Read packets until interrupted or EOF
			while (!interrupted && !packetReader.eof()) {
				if (packetReader.hasAck()) {
					Ack a = packetReader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if (packetReader.hasMessage()) {
					Message m = packetReader.readMessage();
					dbExecutor.execute(new ReceiveMessage(m));
				} else if (packetReader.hasOffer()) {
					Offer o = packetReader.readOffer();
					dbExecutor.execute(new ReceiveOffer(o));
				} else if (packetReader.hasRequest()) {
					Request r = packetReader.readRequest();
					dbExecutor.execute(new ReceiveRequest(r));
				} else {
					throw new FormatException();
				}
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	public void interrupt() {
		// FIXME: This won't interrupt a blocking read
		interrupted = true;
	}

	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
		} else if (e instanceof ShutdownEvent) {
			interrupt();
		}
	}

	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				Transaction txn = db.startTransaction(false);
				try {
					db.receiveAck(txn, contactId, ack);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	private class ReceiveMessage implements Runnable {

		private final Message message;

		private ReceiveMessage(Message message) {
			this.message = message;
		}

		public void run() {
			try {
				Transaction txn = db.startTransaction(false);
				try {
					db.receiveMessage(txn, contactId, message);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	private class ReceiveOffer implements Runnable {

		private final Offer offer;

		private ReceiveOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() {
			try {
				Transaction txn = db.startTransaction(false);
				try {
					db.receiveOffer(txn, contactId, offer);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	private class ReceiveRequest implements Runnable {

		private final Request request;

		private ReceiveRequest(Request request) {
			this.request = request;
		}

		public void run() {
			try {
				Transaction txn = db.startTransaction(false);
				try {
					db.receiveRequest(txn, contactId, request);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}
}
