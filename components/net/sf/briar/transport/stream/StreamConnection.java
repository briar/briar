package net.sf.briar.transport.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseListener;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

abstract class StreamConnection implements Runnable, DatabaseListener {

	private static enum State { SEND_OFFER, IDLE, AWAIT_REQUEST, SEND_BATCHES };

	private static final Logger LOG =
		Logger.getLogger(StreamConnection.class.getName());

	protected final ConnectionReaderFactory connReaderFactory;
	protected final ConnectionWriterFactory connWriterFactory;
	protected final DatabaseComponent db;
	protected final ProtocolReaderFactory protoReaderFactory;
	protected final ProtocolWriterFactory protoWriterFactory;
	protected final ContactId contactId;
	protected final StreamTransportConnection connection;

	// The following fields must only be accessed with this's lock held
	private int writerFlags = 0;
	private Collection<MessageId> outgoingOffer = null;
	private Collection<MessageId> requested = null;
	private Offer incomingOffer = null;

	StreamConnection(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			StreamTransportConnection connection) {
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.db = db;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
		this.contactId = contactId;
		this.connection = connection;
	}

	protected abstract ConnectionReader createConnectionReader()
	throws DbException, IOException;

	protected abstract ConnectionWriter createConnectionWriter()
	throws DbException, IOException ;

	public void eventOccurred(Event e) {
		synchronized(this) {
			if(e == Event.BATCH_RECEIVED)
				writerFlags |= Flags.BATCH_RECEIVED;
			else if(e == Event.CONTACTS_UPDATED)
				writerFlags |= Flags.CONTACTS_UPDATED;
			else if(e == Event.MESSAGES_ADDED)
				writerFlags |= Flags.MESSAGES_ADDED;
			else if(e == Event.SUBSCRIPTIONS_UPDATED)
				writerFlags |= Flags.SUBSCRIPTIONS_UPDATED;
			else if(e == Event.TRANSPORTS_UPDATED)
				writerFlags |= Flags.TRANSPORTS_UPDATED;
			notifyAll();
		}
	}

	public void read() {
		try {
			InputStream in = createConnectionReader().getInputStream();
			ProtocolReader proto = protoReaderFactory.createProtocolReader(in);
			while(!proto.eof()) {
				if(proto.hasAck()) {
					Ack a = proto.readAck();
					db.receiveAck(contactId, a);
				} else if(proto.hasBatch()) {
					Batch b = proto.readBatch();
					db.receiveBatch(contactId, b);
				} else if(proto.hasOffer()) {
					Offer o = proto.readOffer();
					synchronized(this) {
						writerFlags |= Flags.OFFER_RECEIVED;
						incomingOffer = o;
						notifyAll();
					}
				} else if(proto.hasRequest()) {
					Request r = proto.readRequest();
					synchronized(this) {
						writerFlags |= Flags.REQUEST_RECEIVED;
						BitSet b = r.getBitmap();
						// FIXME: Do we need to check the size of the BitSet?
						if(outgoingOffer == null) throw new IOException();
						if(requested != null) throw new IOException();
						// Work out which messages were requested
						requested = new LinkedList<MessageId>();
						Collection<MessageId> seen = new ArrayList<MessageId>();
						int i = 0;
						for(MessageId m : outgoingOffer) {
							if(b.get(i)) requested.add(m);
							else seen.add(m);
							i++;
						}
						// FIXME: Can this be done outside the lock?
						db.setSeen(contactId, seen);
						notifyAll();
					}
				} else if(proto.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = proto.readSubscriptionUpdate();
					db.receiveSubscriptionUpdate(contactId, s);
				} else if(proto.hasTransportUpdate()) {
					TransportUpdate t = proto.readTransportUpdate();
					db.receiveTransportUpdate(contactId, t);
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

	public void write() {
		try {
			OutputStream out = createConnectionWriter().getOutputStream();
			// Create the packet writers
			AckWriter ackWriter = protoWriterFactory.createAckWriter(out);
			BatchWriter batchWriter = protoWriterFactory.createBatchWriter(out);
			OfferWriter offerWriter = protoWriterFactory.createOfferWriter(out);
			RequestWriter requestWriter =
				protoWriterFactory.createRequestWriter(out);
			SubscriptionWriter subscriptionWriter =
				protoWriterFactory.createSubscriptionWriter(out);
			TransportWriter transportWriter =
				protoWriterFactory.createTransportWriter(out);
			// Send the initial packets: transports, subs, any waiting acks
			sendTransports(transportWriter);
			sendSubscriptions(subscriptionWriter);
			sendAcks(ackWriter);
			State state = State.SEND_OFFER;
			// Main loop
			boolean close = false;
			while(!close) {
				int flags = 0;
				switch(state) {

				case SEND_OFFER:
					if(sendOffer(offerWriter)) state = State.AWAIT_REQUEST;
					else state = State.IDLE;
					break;

				case IDLE:
					// Wait for one or more flags to be raised
					synchronized(this) {
						while(writerFlags == 0) {
							try {
								wait();
							} catch(InterruptedException ignored) {}
						}
						flags = writerFlags;
						writerFlags = 0;
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(ackWriter);
					}
					if((flags & Flags.CONTACTS_UPDATED) != 0) {
						if(!db.getContacts().contains(contactId)) {
							close = true;
							break;
						}
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						state = State.SEND_OFFER;
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptions(subscriptionWriter);
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransports(transportWriter);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(requestWriter);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						throw new IOException();
					}
					break;

				case AWAIT_REQUEST:
					// Wait for one or more flags to be raised
					synchronized(this) {
						while(writerFlags == 0) {
							try {
								wait();
							} catch(InterruptedException ignored) {}
						}
						flags = writerFlags;
						writerFlags = 0;
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(ackWriter);
					}
					if((flags & Flags.CONTACTS_UPDATED) != 0) {
						if(!db.getContacts().contains(contactId)) {
							close = true;
							break;
						}
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						// Ignored in this state
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptions(subscriptionWriter);
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransports(transportWriter);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(requestWriter);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						state = State.SEND_BATCHES;
					}
					break;

				case SEND_BATCHES:
					// Deal with any flags that have been raised
					synchronized(this) {
						flags = writerFlags;
						writerFlags = 0;
					}
					if((flags & Flags.BATCH_RECEIVED) != 0) {
						sendAcks(ackWriter);
					}
					if((flags & Flags.CONTACTS_UPDATED) != 0) {
						if(!db.getContacts().contains(contactId)) {
							close = true;
							break;
						}
					}
					if((flags & Flags.MESSAGES_ADDED) != 0) {
						// Ignored in this state
					}
					if((flags & Flags.SUBSCRIPTIONS_UPDATED) != 0) {
						sendSubscriptions(subscriptionWriter);
					}
					if((flags & Flags.TRANSPORTS_UPDATED) != 0) {
						sendTransports(transportWriter);
					}
					if((flags & Flags.OFFER_RECEIVED) != 0) {
						sendRequest(requestWriter);
					}
					if((flags & Flags.REQUEST_RECEIVED) != 0) {
						throw new IOException();
					}
					// Send a batch if possible, otherwise an offer
					if(!sendBatch(batchWriter)) state = State.SEND_OFFER;
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

	private void sendAcks(AckWriter a) throws DbException, IOException {
		while(db.generateAck(contactId, a));
	}

	private boolean sendBatch(BatchWriter b) throws DbException, IOException {
		synchronized(this) {
			assert outgoingOffer == null;
			assert requested != null;
			// FIXME: Can this be done outside the lock?
			boolean anyAdded = db.generateBatch(contactId, b, requested);
			if(!anyAdded) requested = null;
			return anyAdded;
		}
	}

	private boolean sendOffer(OfferWriter o) throws DbException, IOException {
		Collection<MessageId> ids = db.generateOffer(contactId, o);
		synchronized(this) {
			assert outgoingOffer == null;
			assert requested == null;
			outgoingOffer = ids;
		}
		boolean anyOffered = !ids.isEmpty();
		return anyOffered;
	}

	private void sendRequest(RequestWriter r) throws DbException, IOException {
		Offer o;
		synchronized(this) {
			assert incomingOffer != null;
			o = incomingOffer;
			incomingOffer = null;
		}
		db.receiveOffer(contactId, o, r);
	}

	private void sendTransports(TransportWriter t) throws DbException,
	IOException {
		db.generateTransportUpdate(contactId, t);
	}

	private void sendSubscriptions(SubscriptionWriter s) throws DbException,
	IOException {
		db.generateSubscriptionUpdate(contactId, s);
	}
}
