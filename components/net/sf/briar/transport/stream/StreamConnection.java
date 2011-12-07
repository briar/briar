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
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.sf.briar.api.protocol.Batch;
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
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

abstract class StreamConnection implements DatabaseListener {

	private static final Logger LOG =
		Logger.getLogger(StreamConnection.class.getName());

	protected final DatabaseComponent db;
	protected final ConnectionReaderFactory connReaderFactory;
	protected final ConnectionWriterFactory connWriterFactory;
	protected final ProtocolReaderFactory protoReaderFactory;
	protected final ProtocolWriterFactory protoWriterFactory;
	protected final ContactId contactId;
	protected final StreamTransportConnection transport;

	private final Executor dbExecutor, verificationExecutor;
	private final AtomicBoolean canSendOffer;
	private final LinkedList<Runnable> writerTasks; // Locking: this

	private Collection<MessageId> offered = null; // Locking: this

	private volatile ProtocolWriter writer = null;
	private volatile boolean closed = false;

	StreamConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			StreamTransportConnection transport) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.db = db;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
		this.contactId = contactId;
		this.transport = transport;
		canSendOffer = new AtomicBoolean(false);
		writerTasks = new LinkedList<Runnable>();
	}

	protected abstract ConnectionReader createConnectionReader()
	throws DbException, IOException;

	protected abstract ConnectionWriter createConnectionWriter()
	throws DbException, IOException;

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof BatchReceivedEvent) {
			dbExecutor.execute(new GenerateAcks());
		} else if(e instanceof ContactRemovedEvent) {
			ContactId c = ((ContactRemovedEvent) e).getContactId();
			if(contactId.equals(c)) closed = true;
		} else if(e instanceof MessagesAddedEvent) {
			if(canSendOffer.getAndSet(false))
				dbExecutor.execute(new GenerateOffer());
		} else if(e instanceof SubscriptionsUpdatedEvent) {
			Collection<ContactId> affected =
				((SubscriptionsUpdatedEvent) e).getAffectedContacts();
			if(affected.contains(contactId)) {
				dbExecutor.execute(new GenerateSubscriptionUpdate());
			}
		} else if(e instanceof LocalTransportsUpdatedEvent) {
			dbExecutor.execute(new GenerateTransportUpdate());
		}
	}

	void read() {
		try {
			InputStream in = createConnectionReader().getInputStream();
			ProtocolReader reader = protoReaderFactory.createProtocolReader(in);
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasBatch()) {
					UnverifiedBatch b = reader.readBatch();
					verificationExecutor.execute(new VerifyBatch(b));
				} else if(reader.hasOffer()) {
					Offer o = reader.readOffer();
					dbExecutor.execute(new ReceiveOffer(o));
				} else if(reader.hasRequest()) {
					Request r = reader.readRequest();
					// Retrieve the offered message IDs
					Collection<MessageId> offered = getOfferedMessageIds();
					// Work out which messages were requested
					BitSet b = r.getBitmap();
					List<MessageId> requested = new LinkedList<MessageId>();
					List<MessageId> seen = new ArrayList<MessageId>();
					int i = 0;
					for(MessageId m : offered) {
						if(b.get(i++)) requested.add(m);
						else seen.add(m);
					}
					requested = Collections.synchronizedList(requested);
					seen = Collections.unmodifiableList(seen);
					// Mark the unrequested messages as seen
					dbExecutor.execute(new SetSeen(seen));
					// Start sending the requested messages
					dbExecutor.execute(new GenerateBatch(requested));
				} else if(reader.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = reader.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(s));
				} else if(reader.hasTransportUpdate()) {
					TransportUpdate t = reader.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(t));
				} else {
					throw new FormatException();
				}
			}
			transport.dispose(true);
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		}
	}

	private synchronized Collection<MessageId> getOfferedMessageIds()
	throws FormatException {
		if(offered == null) throw new FormatException(); // Unexpected request
		Collection<MessageId> ids = offered;
		offered = null;
		return ids;
	}

	// Locking: this
	private void setOfferedMessageIds(Collection<MessageId> ids) {
		assert offered == null;
		offered = ids;
	}

	void write() {
		try {
			db.addListener(this);
			OutputStream out = createConnectionWriter().getOutputStream();
			writer = protoWriterFactory.createProtocolWriter(out);
			// Send the initial packets: transports, subs, acks, offer
			dbExecutor.execute(new GenerateTransportUpdate());
			dbExecutor.execute(new GenerateSubscriptionUpdate());
			dbExecutor.execute(new GenerateAcks());
			dbExecutor.execute(new GenerateOffer());
			// Main loop
			while(!closed) {
				Runnable task = null;
				synchronized(this) {
					while(writerTasks.isEmpty()) {
						try {
							wait();
						} catch(InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
					task = writerTasks.poll();
				}
				task.run();
			}
			transport.dispose(true);
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		} finally {
			db.removeListener(this);
		}
	}

	// This task runs on a database thread
	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
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

	// This task runs on a verification thread
	private class VerifyBatch implements Runnable {

		private final UnverifiedBatch batch;

		private VerifyBatch(UnverifiedBatch batch) {
			this.batch = batch;
		}

		public void run() {
			try {
				Batch b = batch.verify();
				dbExecutor.execute(new ReceiveBatch(b));
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveBatch implements Runnable {

		private final Batch batch;

		private ReceiveBatch(Batch batch) {
			this.batch = batch;
		}

		public void run() {
			try {
				db.receiveBatch(contactId, batch);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveOffer implements Runnable {

		private final Offer offer;

		private ReceiveOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() {
			try {
				Request r = db.receiveOffer(contactId, offer);
				synchronized(StreamConnection.this) {
					writerTasks.add(new WriteRequest(r));
					StreamConnection.this.notifyAll();
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteRequest implements Runnable {

		private final Request request;

		private WriteRequest(Request request) {
			this.request = request;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeRequest(request);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}

	// This task runs on a database thread
	private class SetSeen implements Runnable {

		private final Collection<MessageId> seen;

		private SetSeen(Collection<MessageId> seen) {
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

	// This task runs on a database thread
	private class ReceiveSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(SubscriptionUpdate update) {
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

	// This task runs on a database thread
	private class ReceiveTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private ReceiveTransportUpdate(TransportUpdate update) {
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

	// This task runs on a database thread
	private class GenerateAcks implements Runnable {

		public void run() {
			assert writer != null;
			int maxBatches = writer.getMaxBatchesForAck(Long.MAX_VALUE);
			try {
				Ack a = db.generateAck(contactId, maxBatches);
				while(a != null) {
					synchronized(StreamConnection.this) {
						writerTasks.add(new WriteAck(a));
						StreamConnection.this.notifyAll();
					}
					a = db.generateAck(contactId, maxBatches);
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteAck implements Runnable {

		private final Ack ack;

		private WriteAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeAck(ack);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}

	// This task runs on a database thred
	private class GenerateBatch implements Runnable {

		private final Collection<MessageId> requested;

		private GenerateBatch(Collection<MessageId> requested) {
			this.requested = requested;
		}

		public void run() {
			assert writer != null;
			int capacity = writer.getMessageCapacityForBatch(Long.MAX_VALUE);
			try {
				RawBatch b = db.generateBatch(contactId, capacity, requested);
				if(b == null) {
					// No batch to write - send another offer
					new GenerateOffer().run();
				} else {
					// Write the batch
					synchronized(StreamConnection.this) {
						writerTasks.add(new WriteBatch(b, requested));
						StreamConnection.this.notifyAll();
					}
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteBatch implements Runnable {

		private final RawBatch batch;
		private final Collection<MessageId> requested;

		private WriteBatch(RawBatch batch, Collection<MessageId> requested) {
			this.batch = batch;
			this.requested = requested;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeBatch(batch);
				if(requested.isEmpty()) {
					// No more batches to send - send another offer
					dbExecutor.execute(new GenerateOffer());
				} else {
					// Send another batch
					dbExecutor.execute(new GenerateBatch(requested));
				}
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateOffer implements Runnable {

		public void run() {
			assert writer != null;
			int maxMessages = writer.getMaxMessagesForOffer(Long.MAX_VALUE);
			try {
				Offer o = db.generateOffer(contactId, maxMessages);
				if(o == null) {
					// No messages to offer - wait for some to be added
					canSendOffer.set(true);
				} else {
					synchronized(StreamConnection.this) {
						// Store the offered message IDs
						setOfferedMessageIds(o.getMessageIds());
						// Write the offer on the writer thread
						writerTasks.add(new WriteOffer(o));
						StreamConnection.this.notifyAll();
					}
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteOffer implements Runnable {

		private final Offer offer;

		private WriteOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeOffer(offer);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateSubscriptionUpdate implements Runnable {

		public void run() {
			try {
				SubscriptionUpdate s = db.generateSubscriptionUpdate(contactId);
				if(s != null) {
					synchronized(StreamConnection.this) {
						writerTasks.add(new WriteSubscriptionUpdate(s));
						StreamConnection.this.notifyAll();
					}
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private WriteSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeSubscriptionUpdate(update);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateTransportUpdate implements Runnable {

		public void run() {
			try {
				TransportUpdate t = db.generateTransportUpdate(contactId);
				if(t != null) {
					synchronized(StreamConnection.this) {
						writerTasks.add(new WriteTransportUpdate(t));
						StreamConnection.this.notifyAll();
					}
				}
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	// This task runs on the writer thread
	private class WriteTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private WriteTransportUpdate(TransportUpdate update) {
			this.update = update;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeTransportUpdate(update);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				transport.dispose(false);
			}
		}
	}
}
