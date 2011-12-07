package net.sf.briar.transport.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.BatchReceivedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessagesAddedEvent;
import net.sf.briar.api.db.event.SubscriptionsUpdatedEvent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

abstract class StreamConnection implements DatabaseListener {

	private static enum State { SEND_OFFER, IDLE, AWAIT_REQUEST, SEND_BATCHES };

	private static final Logger LOG =
		Logger.getLogger(StreamConnection.class.getName());

	protected final Executor dbExecutor;
	protected final DatabaseComponent db;
	protected final SerialComponent serial;
	protected final ConnectionReaderFactory connReaderFactory;
	protected final ConnectionWriterFactory connWriterFactory;
	protected final ProtocolReaderFactory protoReaderFactory;
	protected final ProtocolWriterFactory protoWriterFactory;
	protected final ContactId contactId;
	protected final StreamTransportConnection connection;

	private int writerFlags = 0; // Locking: this
	private Collection<MessageId> offered = null; // Locking: this
	private LinkedList<MessageId> requested = null; // Locking: this
	private Offer incomingOffer = null; // Locking: this

	StreamConnection(@DatabaseExecutor Executor dbExecutor,
			DatabaseComponent db, SerialComponent serial,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			StreamTransportConnection connection) {
		this.dbExecutor = dbExecutor;
		this.db = db;
		this.serial = serial;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
		this.contactId = contactId;
		this.connection = connection;
	}

	protected abstract ConnectionReader createConnectionReader()
	throws DbException, IOException;

	protected abstract ConnectionWriter createConnectionWriter()
	throws DbException, IOException ;

	public void eventOccurred(DatabaseEvent e) {
		synchronized(this) {
			if(e instanceof BatchReceivedEvent) {
				writerFlags |= Flags.BATCH_RECEIVED;
				notifyAll();
			} else if(e instanceof ContactRemovedEvent) {
				ContactId c = ((ContactRemovedEvent) e).getContactId();
				if(contactId.equals(c)) {
					writerFlags |= Flags.CONTACT_REMOVED;
					notifyAll();
				}
			} else if(e instanceof MessagesAddedEvent) {
				writerFlags |= Flags.MESSAGES_ADDED;
				notifyAll();
			} else if(e instanceof SubscriptionsUpdatedEvent) {
				Collection<ContactId> affected =
					((SubscriptionsUpdatedEvent) e).getAffectedContacts();
				if(affected.contains(contactId)) {
					writerFlags |= Flags.SUBSCRIPTIONS_UPDATED;
					notifyAll();
				}
			} else if(e instanceof LocalTransportsUpdatedEvent) {
				writerFlags |= Flags.TRANSPORTS_UPDATED;
				notifyAll();
			}
		}
	}

	void read() {
		try {
			InputStream in = createConnectionReader().getInputStream();
			ProtocolReader proto = protoReaderFactory.createProtocolReader(in);
			while(!proto.eof()) {
				if(proto.hasAck()) {
					Ack a = proto.readAck();
					dbExecutor.execute(new ReceiveAck(contactId, a));
				} else if(proto.hasBatch()) {
					UnverifiedBatch b = proto.readBatch();
					dbExecutor.execute(new ReceiveBatch(contactId, b));
				} else if(proto.hasOffer()) {
					Offer o = proto.readOffer();
					// Store the incoming offer and notify the writer
					synchronized(this) {
						writerFlags |= Flags.OFFER_RECEIVED;
						incomingOffer = o;
						notifyAll();
					}
				} else if(proto.hasRequest()) {
					Request r = proto.readRequest();
					// Retrieve the offered message IDs
					Collection<MessageId> off;
					synchronized(this) {
						if(offered == null)
							throw new IOException("Unexpected request packet");
						off = offered;
						offered = null;
					}
					// Work out which messages were requested
					BitSet b = r.getBitmap();
					LinkedList<MessageId> req = new LinkedList<MessageId>();
					List<MessageId> seen = new ArrayList<MessageId>();
					int i = 0;
					for(MessageId m : off) {
						if(b.get(i++)) req.add(m);
						else seen.add(m);
					}
					seen = Collections.unmodifiableList(seen);
					// Mark the unrequested messages as seen
					dbExecutor.execute(new SetSeen(contactId, seen));
					// Store the requested message IDs and notify the writer
					synchronized(this) {
						if(requested != null)
							throw new IOException("Unexpected request packet");
						requested = req;
						writerFlags |= Flags.REQUEST_RECEIVED;
						notifyAll();
					}
				} else if(proto.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = proto.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(
							contactId, s));
				} else if(proto.hasTransportUpdate()) {
					TransportUpdate t = proto.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(
							contactId, t));
				} else {
					throw new FormatException();
				}
			}
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			connection.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			connection.dispose(false);
		}
		// Success
		connection.dispose(true);
	}

	void write() {
		try {
			OutputStream out = createConnectionWriter().getOutputStream();
			ProtocolWriter proto = protoWriterFactory.createProtocolWriter(out);
			// Send the initial packets: transports, subs, any waiting acks
			sendTransportUpdate(proto);
			sendSubscriptionUpdate(proto);
			sendAcks(proto);
			State state = State.SEND_OFFER;
			// Main loop
			while(true) {
				int flags = 0;
				switch(state) {

				case SEND_OFFER:
					// Try to send an offer
					if(sendOffer(proto)) state = State.AWAIT_REQUEST;
					else state = State.IDLE;
					break;

				case IDLE:
					// Wait for one or more flags to be raised
					synchronized(this) {
						while(writerFlags == 0) {
							try {
								wait();
							} catch(InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
						flags = writerFlags;
						writerFlags = 0;
					}
					// Handle the flags in approximate order of urgency
					if((flags & Flags.CONTACT_REMOVED) != 0) {
						connection.dispose(true);
						return;
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransportUpdate(proto);
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptionUpdate(proto);
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(proto);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(proto);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						// Should only be received in state AWAIT_REQUEST
						throw new IOException("Unexpected request packet");
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						state = State.SEND_OFFER;
					}
					break;

				case AWAIT_REQUEST:
					// Wait for one or more flags to be raised
					synchronized(this) {
						while(writerFlags == 0) {
							try {
								wait();
							} catch(InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
						flags = writerFlags;
						writerFlags = 0;
					}
					// Handle the flags in approximate order of urgency
					if((flags & Flags.CONTACT_REMOVED) != 0) {
						connection.dispose(true);
						return;
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransportUpdate(proto);
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptionUpdate(proto);
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(proto);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(proto);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						state = State.SEND_BATCHES;
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						// Ignored in this state
					}
					break;

				case SEND_BATCHES:
					// Check whether any flags have been raised
					synchronized(this) {
						flags = writerFlags;
						writerFlags = 0;
					}
					// Handle the flags in approximate order of urgency
					if((flags & Flags.CONTACT_REMOVED) != 0) {
						connection.dispose(true);
						return;
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransportUpdate(proto);
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptionUpdate(proto);
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(proto);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(proto);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						// Should only be received in state AWAIT_REQUEST
						throw new IOException("Unexpected request packet");
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						// Ignored in this state
					}
					// Try to send a batch
					if(!sendBatch(proto)) state = State.SEND_OFFER;
					break;
				}
			}
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			connection.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			connection.dispose(false);
		}
		// Success
		connection.dispose(true);
	}

	private void sendAcks(ProtocolWriter proto)
	throws DbException, IOException {
		int maxBatches = proto.getMaxBatchesForAck(Long.MAX_VALUE);
		Ack a = db.generateAck(contactId, maxBatches);
		while(a != null) {
			proto.writeAck(a);
			a = db.generateAck(contactId, maxBatches);
		}
	}

	private boolean sendBatch(ProtocolWriter proto)
	throws DbException, IOException {
		Collection<MessageId> req;
		// Retrieve the requested message IDs
		synchronized(this) {
			assert offered == null;
			assert requested != null;
			req = requested;
		}
		// Try to generate a batch, updating the collection of message IDs
		int capacity = proto.getMessageCapacityForBatch(Long.MAX_VALUE);
		RawBatch b = db.generateBatch(contactId, capacity, req);
		if(b == null) {
			// No more batches can be generated - discard the remaining IDs
			synchronized(this) {
				assert offered == null;
				assert requested == req;
				requested = null;
			}
			return false;
		} else {
			proto.writeBatch(b);
			return true;
		}
	}

	private boolean sendOffer(ProtocolWriter proto)
	throws DbException, IOException {
		// Generate an offer
		int maxMessages = proto.getMaxMessagesForOffer(Long.MAX_VALUE);
		Offer o = db.generateOffer(contactId, maxMessages);
		if(o == null) return false;
		proto.writeOffer(o);
		// Store the offered message IDs
		synchronized(this) {
			assert offered == null;
			assert requested == null;
			offered = o.getMessageIds();
		}
		return true;
	}

	private void sendRequest(ProtocolWriter proto)
	throws DbException, IOException {
		Offer o;
		// Retrieve the incoming offer
		synchronized(this) {
			assert incomingOffer != null;
			o = incomingOffer;
			incomingOffer = null;
		}
		// Process the offer and generate a request
		Request r = db.receiveOffer(contactId, o);
		proto.writeRequest(r);
	}

	private void sendTransportUpdate(ProtocolWriter proto)
	throws DbException, IOException {
		TransportUpdate t = db.generateTransportUpdate(contactId);
		if(t != null) proto.writeTransportUpdate(t);
	}

	private void sendSubscriptionUpdate(ProtocolWriter proto)
	throws DbException, IOException {
		SubscriptionUpdate s = db.generateSubscriptionUpdate(contactId);
		if(s != null) proto.writeSubscriptionUpdate(s);
	}

	private class ReceiveAck implements Runnable {

		private final ContactId contactId;
		private final Ack ack;

		private ReceiveAck(ContactId contactId, Ack ack) {
			this.contactId = contactId;
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveBatch implements Runnable {

		private final ContactId contactId;
		private final UnverifiedBatch batch;

		private ReceiveBatch(ContactId contactId, UnverifiedBatch batch) {
			this.contactId = contactId;
			this.batch = batch;
		}

		public void run() {
			try {
				// FIXME: Don't verify on the DB thread
				db.receiveBatch(contactId, batch.verify());
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class SetSeen implements Runnable {

		private final ContactId contactId;
		private final Collection<MessageId> seen;

		private SetSeen(ContactId contactId, Collection<MessageId> seen) {
			this.contactId = contactId;
			this.seen = seen;
		}

		public void run() {
			try {
				db.setSeen(contactId, seen);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveSubscriptionUpdate implements Runnable {

		private final ContactId contactId;
		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(ContactId contactId,
				SubscriptionUpdate update) {
			this.contactId = contactId;
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveTransportUpdate implements Runnable {

		private final ContactId contactId;
		private final TransportUpdate update;

		private ReceiveTransportUpdate(ContactId contactId,
				TransportUpdate update) {
			this.contactId = contactId;
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}
}
