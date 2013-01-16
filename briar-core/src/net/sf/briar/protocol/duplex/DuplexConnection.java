package net.sf.briar.protocol.duplex;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageReceivedEvent;
import net.sf.briar.api.db.event.SubscriptionsUpdatedEvent;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.MessageVerifier;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

abstract class DuplexConnection implements DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(DuplexConnection.class.getName());

	private static final Runnable CLOSE = new Runnable() {
		public void run() {}
	};

	protected final DatabaseComponent db;
	protected final ConnectionRegistry connRegistry;
	protected final ConnectionReaderFactory connReaderFactory;
	protected final ConnectionWriterFactory connWriterFactory;
	protected final ProtocolReaderFactory protoReaderFactory;
	protected final ProtocolWriterFactory protoWriterFactory;
	protected final ConnectionContext ctx;
	protected final DuplexTransportConnection transport;
	protected final ContactId contactId;
	protected final TransportId transportId;

	private final Executor dbExecutor, verificationExecutor;
	private final MessageVerifier messageVerifier;
	private final AtomicBoolean canSendOffer, disposed;
	private final BlockingQueue<Runnable> writerTasks;

	private Collection<MessageId> offered = null; // Locking: this

	private volatile ProtocolWriter writer = null;

	DuplexConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ConnectionContext ctx,
			DuplexTransportConnection transport) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
		canSendOffer = new AtomicBoolean(false);
		disposed = new AtomicBoolean(false);
		writerTasks = new LinkedBlockingQueue<Runnable>();
	}

	protected abstract ConnectionReader createConnectionReader()
			throws IOException;

	protected abstract ConnectionWriter createConnectionWriter()
			throws IOException;

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof MessageReceivedEvent) {
			dbExecutor.execute(new GenerateAcks());
		} else if(e instanceof ContactRemovedEvent) {
			ContactId c = ((ContactRemovedEvent) e).getContactId();
			if(contactId.equals(c)) dispose(false, true);
		} else if(e instanceof MessageAddedEvent) {
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
				} else if(reader.hasMessage()) {
					UnverifiedMessage m = reader.readMessage();
					verificationExecutor.execute(new VerifyMessage(m));
				} else if(reader.hasOffer()) {
					Offer o = reader.readOffer();
					dbExecutor.execute(new ReceiveOffer(o));
				} else if(reader.hasRequest()) {
					Request r = reader.readRequest();
					// Retrieve the offered message IDs
					Collection<MessageId> offered = getOfferedMessageIds();
					if(offered == null) throw new FormatException();
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
					dbExecutor.execute(new GenerateBatches(requested));
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
			// The writer will dispose of the transport if no exceptions occur
			writerTasks.add(CLOSE);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		}
	}

	private synchronized Collection<MessageId> getOfferedMessageIds() {
		Collection<MessageId> ids = offered;
		offered = null;
		return ids;
	}

	private synchronized void setOfferedMessageIds(Collection<MessageId> ids) {
		assert offered == null;
		offered = ids;
	}

	void write() {
		connRegistry.registerConnection(contactId, transportId);
		db.addListener(this);
		try {
			OutputStream out = createConnectionWriter().getOutputStream();
			writer = protoWriterFactory.createProtocolWriter(out,
					transport.shouldFlush());
			// Send the initial packets: transports, subs, acks, offer
			dbExecutor.execute(new GenerateTransportUpdate());
			dbExecutor.execute(new GenerateSubscriptionUpdate());
			dbExecutor.execute(new GenerateAcks());
			dbExecutor.execute(new GenerateOffer());
			// Main loop
			while(true) {
				Runnable task = writerTasks.take();
				if(task == CLOSE) break;
				task.run();
			}
			writer.flush();
			writer.close();
			dispose(false, true);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while waiting for task");
			dispose(true, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		} finally {
			connRegistry.unregisterConnection(contactId, transportId);
			db.removeListener(this);
		}
	}

	private void dispose(boolean exception, boolean recognised) {
		if(disposed.getAndSet(true)) return;
		ByteUtils.erase(ctx.getSecret());
		try {
			transport.dispose(exception, recognised);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a verification thread
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

	// This task runs on a database thread
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

	// This task runs on a database thread
	private class ReceiveOffer implements Runnable {

		private final Offer offer;

		private ReceiveOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() {
			try {
				Request r = db.receiveOffer(contactId, offer);
				writerTasks.add(new WriteRequest(r));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateAcks implements Runnable {

		public void run() {
			assert writer != null;
			int maxMessages = writer.getMaxMessagesForAck(Long.MAX_VALUE);
			try {
				Ack a = db.generateAck(contactId, maxMessages);
				if(a != null) writerTasks.add(new WriteAck(a));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				dbExecutor.execute(new GenerateAcks());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thred
	private class GenerateBatches implements Runnable {

		private final Collection<MessageId> requested;

		private GenerateBatches(Collection<MessageId> requested) {
			this.requested = requested;
		}

		public void run() {
			assert writer != null;
			try {
				Collection<byte[]> batch = db.generateBatch(contactId,
						Integer.MAX_VALUE, requested);
				if(batch == null) new GenerateOffer().run();
				else writerTasks.add(new WriteBatch(batch, requested));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the writer thread
	private class WriteBatch implements Runnable {

		private final Collection<byte[]> batch;
		private final Collection<MessageId> requested;

		private WriteBatch(Collection<byte[]> batch,
				Collection<MessageId> requested) {
			this.batch = batch;
			this.requested = requested;
		}

		public void run() {
			assert writer != null;
			try {
				for(byte[] raw : batch) writer.writeMessage(raw);
				if(requested.isEmpty()) dbExecutor.execute(new GenerateOffer());
				else dbExecutor.execute(new GenerateBatches(requested));
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
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
					// Store the offered message IDs
					setOfferedMessageIds(o.getMessageIds());
					// Write the offer on the writer thread
					writerTasks.add(new WriteOffer(o));
				}
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateSubscriptionUpdate implements Runnable {

		public void run() {
			try {
				SubscriptionUpdate s = db.generateSubscriptionUpdate(contactId);
				if(s != null) writerTasks.add(new WriteSubscriptionUpdate(s));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateTransportUpdate implements Runnable {

		public void run() {
			try {
				TransportUpdate t = db.generateTransportUpdate(contactId);
				if(t != null) writerTasks.add(new WriteTransportUpdate(t));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}
}
