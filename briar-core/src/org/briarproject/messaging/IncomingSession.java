package org.briarproject.messaging;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.PacketReader;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;

/**
 * An incoming {@link org.briarproject.api.messaging.MessagingSession
 * MessagingSession}.
 */
class IncomingSession implements MessagingSession, EventListener {

	private static final Logger LOG =
			Logger.getLogger(IncomingSession.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor, cryptoExecutor;
	private final EventBus eventBus;
	private final MessageVerifier messageVerifier;
	private final ContactId contactId;
	private final TransportId transportId;
	private final PacketReader packetReader;

	private volatile boolean interrupted = false;

	IncomingSession(DatabaseComponent db, Executor dbExecutor,
			Executor cryptoExecutor, EventBus eventBus,
			MessageVerifier messageVerifier,
			PacketReaderFactory packetReaderFactory, ContactId contactId,
			TransportId transportId, InputStream in) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.eventBus = eventBus;
		this.messageVerifier = messageVerifier;
		this.contactId = contactId;
		this.transportId = transportId;
		packetReader = packetReaderFactory.createPacketReader(in);
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Read packets until interrupted or EOF
			while(!interrupted && !packetReader.eof()) {
				if(packetReader.hasAck()) {
					Ack a = packetReader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if(packetReader.hasMessage()) {
					UnverifiedMessage m = packetReader.readMessage();
					cryptoExecutor.execute(new VerifyMessage(m));
				} else if(packetReader.hasRetentionAck()) {
					RetentionAck a = packetReader.readRetentionAck();
					dbExecutor.execute(new ReceiveRetentionAck(a));
				} else if(packetReader.hasRetentionUpdate()) {
					RetentionUpdate u = packetReader.readRetentionUpdate();
					dbExecutor.execute(new ReceiveRetentionUpdate(u));
				} else if(packetReader.hasSubscriptionAck()) {
					SubscriptionAck a = packetReader.readSubscriptionAck();
					dbExecutor.execute(new ReceiveSubscriptionAck(a));
				} else if(packetReader.hasSubscriptionUpdate()) {
					SubscriptionUpdate u = packetReader.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(u));
				} else if(packetReader.hasTransportAck()) {
					TransportAck a = packetReader.readTransportAck();
					dbExecutor.execute(new ReceiveTransportAck(a));
				} else if(packetReader.hasTransportUpdate()) {
					TransportUpdate u = packetReader.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(u));
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
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(c.getContactId().equals(contactId)) interrupt();
		} else if(e instanceof ShutdownEvent) {
			interrupt();
		} else if(e instanceof TransportRemovedEvent) {
			TransportRemovedEvent t = (TransportRemovedEvent) e;
			if(t.getTransportId().equals(transportId)) interrupt();
		}
	}

	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class VerifyMessage implements Runnable {

		private final UnverifiedMessage message;

		private VerifyMessage(UnverifiedMessage message) {
			this.message = message;
		}

		public void run() {
			try {
				Message m = messageVerifier.verifyMessage(message);
				dbExecutor.execute(new ReceiveMessage(m));
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				db.receiveMessage(contactId, message);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveRetentionAck implements Runnable {

		private final RetentionAck ack;

		private ReceiveRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveRetentionAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveRetentionUpdate implements Runnable {

		private final RetentionUpdate update;

		private ReceiveRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveRetentionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveSubscriptionAck implements Runnable {

		private final SubscriptionAck ack;

		private ReceiveSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveSubscriptionAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveTransportAck implements Runnable {

		private final TransportAck ack;

		private ReceiveTransportAck(TransportAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveTransportAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private ReceiveTransportUpdate(TransportUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
