package net.sf.briar.messaging.duplex;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.db.AckAndRequest;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.LocalSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.MessageReceivedEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
import net.sf.briar.api.db.event.RemoteRetentionTimeUpdatedEvent;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.PacketReader;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
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

	private static final Runnable DIE = new Runnable() {
		public void run() {}
	};

	protected final DatabaseComponent db;
	protected final ConnectionRegistry connRegistry;
	protected final ConnectionReaderFactory connReaderFactory;
	protected final ConnectionWriterFactory connWriterFactory;
	protected final PacketReaderFactory packetReaderFactory;
	protected final PacketWriterFactory packetWriterFactory;
	protected final ConnectionContext ctx;
	protected final DuplexTransportConnection transport;
	protected final ContactId contactId;
	protected final TransportId transportId;

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final long maxLatency;
	private final AtomicBoolean canSendOffer, disposed;
	private final BlockingQueue<Runnable> writerTasks;

	private volatile PacketWriter writer = null;

	DuplexConnection(Executor dbExecutor, Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory, ConnectionContext ctx,
			DuplexTransportConnection transport) {
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.packetReaderFactory = packetReaderFactory;
		this.packetWriterFactory = packetWriterFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
		maxLatency = transport.getMaxLatency();
		canSendOffer = new AtomicBoolean(true);
		disposed = new AtomicBoolean(false);
		writerTasks = new LinkedBlockingQueue<Runnable>();
	}

	protected abstract ConnectionReader createConnectionReader()
			throws IOException;

	protected abstract ConnectionWriter createConnectionWriter()
			throws IOException;

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(contactId.equals(c.getContactId())) writerTasks.add(CLOSE);
		} else if(e instanceof GroupMessageAddedEvent) {
			if(canSendOffer.getAndSet(false))
				dbExecutor.execute(new GenerateOffer());
		} else if(e instanceof MessageExpiredEvent) {
			dbExecutor.execute(new GenerateRetentionUpdate());
		} else if(e instanceof LocalSubscriptionsUpdatedEvent) {
			LocalSubscriptionsUpdatedEvent l =
					(LocalSubscriptionsUpdatedEvent) e;
			if(l.getAffectedContacts().contains(contactId)) {
				dbExecutor.execute(new GenerateSubscriptionUpdate());
				if(canSendOffer.getAndSet(false))
					dbExecutor.execute(new GenerateOffer());
			}
		} else if(e instanceof LocalTransportsUpdatedEvent) {
			dbExecutor.execute(new GenerateTransportUpdates());
		} else if(e instanceof MessageReceivedEvent) {
			if(((MessageReceivedEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateAcks());
		} else if(e instanceof PrivateMessageAddedEvent) {
			PrivateMessageAddedEvent p = (PrivateMessageAddedEvent) e;
			if(!p.isIncoming() && p.getContactId().equals(contactId)) {
				if(canSendOffer.getAndSet(false))
					dbExecutor.execute(new GenerateOffer());
			}
		} else if(e instanceof RemoteRetentionTimeUpdatedEvent) {
			dbExecutor.execute(new GenerateRetentionAck());
		} else if(e instanceof RemoteSubscriptionsUpdatedEvent) {
			dbExecutor.execute(new GenerateSubscriptionAck());
			if(canSendOffer.getAndSet(false))
				dbExecutor.execute(new GenerateOffer());
		} else if(e instanceof RemoteTransportsUpdatedEvent) {
			dbExecutor.execute(new GenerateTransportAcks());
		}
	}

	void read() {
		try {
			InputStream in = createConnectionReader().getInputStream();
			PacketReader reader = packetReaderFactory.createPacketReader(in);
			if(LOG.isLoggable(INFO)) LOG.info("Starting to read");
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					if(LOG.isLoggable(INFO)) LOG.info("Received ack");
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasMessage()) {
					UnverifiedMessage m = reader.readMessage();
					if(LOG.isLoggable(INFO)) LOG.info("Received message");
					cryptoExecutor.execute(new VerifyMessage(m));
				} else if(reader.hasOffer()) {
					Offer o = reader.readOffer();
					if(LOG.isLoggable(INFO)) LOG.info("Received offer");
					dbExecutor.execute(new ReceiveOffer(o));
				} else if(reader.hasRequest()) {
					Request r = reader.readRequest();
					if(LOG.isLoggable(INFO)) LOG.info("Received request");
					// Make a mutable copy of the requested IDs
					Collection<MessageId> requested = r.getMessageIds();
					requested = new ArrayList<MessageId>(requested);
					dbExecutor.execute(new GenerateBatches(requested));
				} else if(reader.hasRetentionAck()) {
					RetentionAck a = reader.readRetentionAck();
					if(LOG.isLoggable(INFO)) LOG.info("Received retention ack");
					dbExecutor.execute(new ReceiveRetentionAck(a));
				} else if(reader.hasRetentionUpdate()) {
					RetentionUpdate u = reader.readRetentionUpdate();
					if(LOG.isLoggable(INFO))
						LOG.info("Received retention update");
					dbExecutor.execute(new ReceiveRetentionUpdate(u));
				} else if(reader.hasSubscriptionAck()) {
					SubscriptionAck a = reader.readSubscriptionAck();
					if(LOG.isLoggable(INFO))
						LOG.info("Received subscription ack");
					dbExecutor.execute(new ReceiveSubscriptionAck(a));
				} else if(reader.hasSubscriptionUpdate()) {
					SubscriptionUpdate u = reader.readSubscriptionUpdate();
					if(LOG.isLoggable(INFO))
						LOG.info("Received subscription update");
					dbExecutor.execute(new ReceiveSubscriptionUpdate(u));
				} else if(reader.hasTransportAck()) {
					TransportAck a = reader.readTransportAck();
					if(LOG.isLoggable(INFO))
						LOG.info("Received transport ack");
					dbExecutor.execute(new ReceiveTransportAck(a));
				} else if(reader.hasTransportUpdate()) {
					TransportUpdate u = reader.readTransportUpdate();
					if(LOG.isLoggable(INFO))
						LOG.info("Received transport update");
					dbExecutor.execute(new ReceiveTransportUpdate(u));
				} else {
					throw new FormatException();
				}
			}
			if(LOG.isLoggable(INFO)) LOG.info("Finished reading");
			writerTasks.add(CLOSE);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			writerTasks.add(DIE);
		}
	}

	void write() {
		connRegistry.registerConnection(contactId, transportId);
		db.addListener(this);
		try {
			OutputStream out = createConnectionWriter().getOutputStream();
			writer = packetWriterFactory.createPacketWriter(out,
					transport.shouldFlush());
			if(LOG.isLoggable(INFO)) LOG.info("Starting to write");
			// Send the initial packets: updates, acks, offer
			dbExecutor.execute(new GenerateTransportAcks());
			dbExecutor.execute(new GenerateTransportUpdates());
			dbExecutor.execute(new GenerateSubscriptionAck());
			dbExecutor.execute(new GenerateSubscriptionUpdate());
			dbExecutor.execute(new GenerateRetentionAck());
			dbExecutor.execute(new GenerateRetentionUpdate());
			dbExecutor.execute(new GenerateAcks());
			if(canSendOffer.getAndSet(false))
				dbExecutor.execute(new GenerateOffer());
			// Main loop
			Runnable task = null;
			while(true) {
				if(LOG.isLoggable(INFO))
					LOG.info("Waiting for something to write");
				task = writerTasks.take();
				if(task == CLOSE || task == DIE) break;
				task.run();
			}
			if(LOG.isLoggable(INFO)) LOG.info("Finished writing");
			if(task == CLOSE) {
				writer.flush();
				writer.close();
				dispose(false, true);
			} else {
				dispose(true, true);
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while waiting for task");
			dispose(true, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		}
		db.removeListener(this);
		connRegistry.unregisterConnection(contactId, transportId);
	}

	private void dispose(boolean exception, boolean recognised) {
		if(disposed.getAndSet(true)) return;
		if(LOG.isLoggable(INFO))
			LOG.info("Disposing: " + exception + ", " + recognised);
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
				if(LOG.isLoggable(INFO)) LOG.info("DB received ack");
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
				if(LOG.isLoggable(INFO)) LOG.info("Verified message");
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
				if(LOG.isLoggable(INFO)) LOG.info("DB received message");
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
				AckAndRequest ar = db.receiveOffer(contactId, offer);
				Ack a = ar.getAck();
				Request r = ar.getRequest();
				if(LOG.isLoggable(INFO)) {
					LOG.info("DB received offer: " + (a != null)
							+ " " + (r != null));
				}
				if(a != null) writerTasks.add(new WriteAck(a));
				if(r != null) writerTasks.add(new WriteRequest(r));
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
				if(LOG.isLoggable(INFO)) LOG.info("Sent request");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveRetentionAck implements Runnable {

		private final RetentionAck ack;

		private ReceiveRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveRetentionAck(contactId, ack);
				if(LOG.isLoggable(INFO)) LOG.info("DB received retention ack");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveRetentionUpdate implements Runnable {

		private final RetentionUpdate update;

		private ReceiveRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveRetentionUpdate(contactId, update);
				if(LOG.isLoggable(INFO))
					LOG.info("DB received retention update");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveSubscriptionAck implements Runnable {

		private final SubscriptionAck ack;

		private ReceiveSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveSubscriptionAck(contactId, ack);
				if(LOG.isLoggable(INFO))
					LOG.info("DB received subscription ack");
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
				if(LOG.isLoggable(INFO))
					LOG.info("DB received subscription update");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a database thread
	private class ReceiveTransportAck implements Runnable {

		private final TransportAck ack;

		private ReceiveTransportAck(TransportAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveTransportAck(contactId, ack);
				if(LOG.isLoggable(INFO)) LOG.info("DB received transport ack");
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
				if(LOG.isLoggable(INFO))
					LOG.info("DB received transport update");
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
				if(LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
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
				if(LOG.isLoggable(INFO)) LOG.info("Sent ack");
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
						MAX_PACKET_LENGTH, maxLatency, requested);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (batch != null));
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
				if(LOG.isLoggable(INFO)) LOG.info("Sent batch");
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
				if(LOG.isLoggable(INFO))
					LOG.info("Generated offer: " + (o != null));
				if(o == null) canSendOffer.set(true);
				else writerTasks.add(new WriteOffer(o));
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
				if(LOG.isLoggable(INFO)) LOG.info("Sent offer");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateRetentionAck implements Runnable {

		public void run() {
			try {
				RetentionAck a = db.generateRetentionAck(contactId);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated retention ack: " + (a != null));
				if(a != null) writerTasks.add(new WriteRetentionAck(a));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteRetentionAck implements Runnable {

		private final RetentionAck ack;

		private WriteRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeRetentionAck(ack);
				if(LOG.isLoggable(INFO)) LOG.info("Sent retention ack");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateRetentionUpdate implements Runnable {

		public void run() {
			try {
				RetentionUpdate u =
						db.generateRetentionUpdate(contactId, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated retention update: " + (u != null));
				if(u != null) writerTasks.add(new WriteRetentionUpdate(u));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the writer thread
	private class WriteRetentionUpdate implements Runnable {

		private final RetentionUpdate update;

		private WriteRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeRetentionUpdate(update);
				if(LOG.isLoggable(INFO)) LOG.info("Sent retention update");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateSubscriptionAck implements Runnable {

		public void run() {
			try {
				SubscriptionAck a = db.generateSubscriptionAck(contactId);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated subscription ack: " + (a != null));
				if(a != null) writerTasks.add(new WriteSubscriptionAck(a));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteSubscriptionAck implements Runnable {

		private final SubscriptionAck ack;

		private WriteSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() {
			assert writer != null;
			try {
				writer.writeSubscriptionAck(ack);
				if(LOG.isLoggable(INFO)) LOG.info("Sent subscription ack");
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
				SubscriptionUpdate u =
						db.generateSubscriptionUpdate(contactId, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated subscription update: " + (u != null));
				if(u != null) writerTasks.add(new WriteSubscriptionUpdate(u));
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
				if(LOG.isLoggable(INFO)) LOG.info("Sent subscription update");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateTransportAcks implements Runnable {

		public void run() {
			try {
				Collection<TransportAck> acks =
						db.generateTransportAcks(contactId);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated transport acks: " + (acks != null));
				if(acks != null) writerTasks.add(new WriteTransportAcks(acks));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteTransportAcks implements Runnable {

		private final Collection<TransportAck> acks;

		private WriteTransportAcks(Collection<TransportAck> acks) {
			this.acks = acks;
		}

		public void run() {
			assert writer != null;
			try {
				for(TransportAck a : acks) writer.writeTransportAck(a);
				if(LOG.isLoggable(INFO)) LOG.info("Sent transport acks");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on a database thread
	private class GenerateTransportUpdates implements Runnable {

		public void run() {
			try {
				Collection<TransportUpdate> t =
						db.generateTransportUpdates(contactId, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated transport updates: " + (t != null));
				if(t != null) writerTasks.add(new WriteTransportUpdates(t));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the writer thread
	private class WriteTransportUpdates implements Runnable {

		private final Collection<TransportUpdate> updates;

		private WriteTransportUpdates(Collection<TransportUpdate> updates) {
			this.updates = updates;
		}

		public void run() {
			assert writer != null;
			try {
				for(TransportUpdate u : updates) writer.writeTransportUpdate(u);
				if(LOG.isLoggable(INFO)) LOG.info("Sent transport updates");
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}
}
