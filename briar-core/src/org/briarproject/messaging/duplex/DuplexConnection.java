package org.briarproject.messaging.duplex;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.LocalTransportsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.RemoteRetentionTimeUpdatedEvent;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.RemoteTransportsUpdatedEvent;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.PacketReader;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReader;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.util.ByteUtils;

abstract class DuplexConnection implements EventListener {

	private static final Logger LOG =
			Logger.getLogger(DuplexConnection.class.getName());

	private static final Runnable CLOSE = new Runnable() {
		public void run() {}
	};

	private static final Runnable DIE = new Runnable() {
		public void run() {}
	};

	protected final DatabaseComponent db;
	protected final EventBus eventBus;
	protected final ConnectionRegistry connRegistry;
	protected final StreamReaderFactory connReaderFactory;
	protected final StreamWriterFactory connWriterFactory;
	protected final PacketReaderFactory packetReaderFactory;
	protected final PacketWriterFactory packetWriterFactory;
	protected final StreamContext ctx;
	protected final DuplexTransportConnection transport;
	protected final ContactId contactId;
	protected final TransportId transportId;

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final long maxLatency;
	private final AtomicBoolean disposed;
	private final BlockingQueue<Runnable> writerTasks;

	private volatile PacketWriter writer = null;

	DuplexConnection(Executor dbExecutor, Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			EventBus eventBus, ConnectionRegistry connRegistry,
			StreamReaderFactory connReaderFactory,
			StreamWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory, StreamContext ctx,
			DuplexTransportConnection transport) {
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.eventBus = eventBus;
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
		disposed = new AtomicBoolean(false);
		writerTasks = new LinkedBlockingQueue<Runnable>();
	}

	protected abstract StreamReader createStreamReader() throws IOException;

	protected abstract StreamWriter createStreamWriter() throws IOException;

	public void eventOccurred(Event e) {
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(contactId.equals(c.getContactId())) writerTasks.add(CLOSE);
		} else if(e instanceof MessageAddedEvent) {
			dbExecutor.execute(new GenerateOffer());
		} else if(e instanceof MessageExpiredEvent) {
			dbExecutor.execute(new GenerateRetentionUpdate());
		} else if(e instanceof LocalSubscriptionsUpdatedEvent) {
			LocalSubscriptionsUpdatedEvent l =
					(LocalSubscriptionsUpdatedEvent) e;
			if(l.getAffectedContacts().contains(contactId)) {
				dbExecutor.execute(new GenerateSubscriptionUpdate());
				dbExecutor.execute(new GenerateOffer());
			}
		} else if(e instanceof LocalTransportsUpdatedEvent) {
			dbExecutor.execute(new GenerateTransportUpdates());
		} else if(e instanceof MessageRequestedEvent) {
			if(((MessageRequestedEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateBatch());
		} else if(e instanceof MessageToAckEvent) {
			if(((MessageToAckEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateAck());
		} else if(e instanceof MessageToRequestEvent) {
			if(((MessageToRequestEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateRequest());
		} else if(e instanceof RemoteRetentionTimeUpdatedEvent) {
			dbExecutor.execute(new GenerateRetentionAck());
		} else if(e instanceof RemoteSubscriptionsUpdatedEvent) {
			dbExecutor.execute(new GenerateSubscriptionAck());
			dbExecutor.execute(new GenerateOffer());
		} else if(e instanceof RemoteTransportsUpdatedEvent) {
			dbExecutor.execute(new GenerateTransportAcks());
		}
	}

	void read() {
		try {
			InputStream in = createStreamReader().getInputStream();
			PacketReader reader = packetReaderFactory.createPacketReader(in);
			LOG.info("Starting to read");
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					LOG.info("Received ack");
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasMessage()) {
					UnverifiedMessage m = reader.readMessage();
					LOG.info("Received message");
					cryptoExecutor.execute(new VerifyMessage(m));
				} else if(reader.hasOffer()) {
					Offer o = reader.readOffer();
					LOG.info("Received offer");
					dbExecutor.execute(new ReceiveOffer(o));
				} else if(reader.hasRequest()) {
					Request r = reader.readRequest();
					LOG.info("Received request");
					dbExecutor.execute(new ReceiveRequest(r));
				} else if(reader.hasRetentionAck()) {
					RetentionAck a = reader.readRetentionAck();
					LOG.info("Received retention ack");
					dbExecutor.execute(new ReceiveRetentionAck(a));
				} else if(reader.hasRetentionUpdate()) {
					RetentionUpdate u = reader.readRetentionUpdate();
					LOG.info("Received retention update");
					dbExecutor.execute(new ReceiveRetentionUpdate(u));
				} else if(reader.hasSubscriptionAck()) {
					SubscriptionAck a = reader.readSubscriptionAck();
					LOG.info("Received subscription ack");
					dbExecutor.execute(new ReceiveSubscriptionAck(a));
				} else if(reader.hasSubscriptionUpdate()) {
					SubscriptionUpdate u = reader.readSubscriptionUpdate();
					LOG.info("Received subscription update");
					dbExecutor.execute(new ReceiveSubscriptionUpdate(u));
				} else if(reader.hasTransportAck()) {
					TransportAck a = reader.readTransportAck();
					LOG.info("Received transport ack");
					dbExecutor.execute(new ReceiveTransportAck(a));
				} else if(reader.hasTransportUpdate()) {
					TransportUpdate u = reader.readTransportUpdate();
					LOG.info("Received transport update");
					dbExecutor.execute(new ReceiveTransportUpdate(u));
				} else {
					throw new FormatException();
				}
			}
			LOG.info("Finished reading");
			writerTasks.add(CLOSE);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			writerTasks.add(DIE);
		}
	}

	void write() {
		connRegistry.registerConnection(contactId, transportId);
		eventBus.addListener(this);
		try {
			OutputStream out = createStreamWriter().getOutputStream();
			writer = packetWriterFactory.createPacketWriter(out, true);
			LOG.info("Starting to write");
			// Ensure the tag is sent
			out.flush();
			// Send the initial packets
			dbExecutor.execute(new GenerateTransportAcks());
			dbExecutor.execute(new GenerateTransportUpdates());
			dbExecutor.execute(new GenerateSubscriptionAck());
			dbExecutor.execute(new GenerateSubscriptionUpdate());
			dbExecutor.execute(new GenerateRetentionAck());
			dbExecutor.execute(new GenerateRetentionUpdate());
			dbExecutor.execute(new GenerateAck());
			dbExecutor.execute(new GenerateBatch());
			dbExecutor.execute(new GenerateOffer());
			dbExecutor.execute(new GenerateRequest());
			// Main loop
			Runnable task = null;
			while(true) {
				LOG.info("Waiting for something to write");
				task = writerTasks.take();
				if(task == CLOSE || task == DIE) break;
				task.run();
			}
			LOG.info("Finished writing");
			if(task == CLOSE) {
				writer.flush();
				writer.close();
				dispose(false, true);
			} else {
				dispose(true, true);
			}
		} catch(InterruptedException e) {
			LOG.warning("Interrupted while waiting for task");
			Thread.currentThread().interrupt();
			dispose(true, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		}
		eventBus.removeListener(this);
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

	// This task runs on the database thread
	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
				LOG.info("DB received ack");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on a crypto thread
	private class VerifyMessage implements Runnable {

		private final UnverifiedMessage message;

		private VerifyMessage(UnverifiedMessage message) {
			this.message = message;
		}

		public void run() {
			try {
				Message m = messageVerifier.verifyMessage(message);
				LOG.info("Verified message");
				dbExecutor.execute(new ReceiveMessage(m));
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveMessage implements Runnable {

		private final Message message;

		private ReceiveMessage(Message message) {
			this.message = message;
		}

		public void run() {
			try {
				db.receiveMessage(contactId, message);
				LOG.info("DB received message");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveOffer implements Runnable {

		private final Offer offer;

		private ReceiveOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() {
			try {
				db.receiveOffer(contactId, offer);
				LOG.info("DB received offer");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveRequest implements Runnable {

		private final Request request;

		private ReceiveRequest(Request request) {
			this.request = request;
		}

		public void run() {
			try {
				db.receiveRequest(contactId, request);
				LOG.info("DB received request");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveRetentionAck implements Runnable {

		private final RetentionAck ack;

		private ReceiveRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveRetentionAck(contactId, ack);
				LOG.info("DB received retention ack");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveRetentionUpdate implements Runnable {

		private final RetentionUpdate update;

		private ReceiveRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveRetentionUpdate(contactId, update);
				LOG.info("DB received retention update");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveSubscriptionAck implements Runnable {

		private final SubscriptionAck ack;

		private ReceiveSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveSubscriptionAck(contactId, ack);
				LOG.info("DB received subscription ack");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
				LOG.info("DB received subscription update");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveTransportAck implements Runnable {

		private final TransportAck ack;

		private ReceiveTransportAck(TransportAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveTransportAck(contactId, ack);
				LOG.info("DB received transport ack");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class ReceiveTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private ReceiveTransportUpdate(TransportUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
				LOG.info("DB received transport update");
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the database thread
	private class GenerateAck implements Runnable {

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
				LOG.info("Sent ack");
				dbExecutor.execute(new GenerateAck());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
	private class GenerateBatch implements Runnable {

		public void run() {
			assert writer != null;
			try {
				Collection<byte[]> b = db.generateRequestedBatch(contactId,
						MAX_PACKET_LENGTH, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if(b != null) writerTasks.add(new WriteBatch(b));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	// This task runs on the writer thread
	private class WriteBatch implements Runnable {

		private final Collection<byte[]> batch;

		private WriteBatch(Collection<byte[]> batch) {
			this.batch = batch;
		}

		public void run() {
			assert writer != null;
			try {
				for(byte[] raw : batch) writer.writeMessage(raw);
				LOG.info("Sent batch");
				dbExecutor.execute(new GenerateBatch());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
	private class GenerateOffer implements Runnable {

		public void run() {
			assert writer != null;
			int maxMessages = writer.getMaxMessagesForOffer(Long.MAX_VALUE);
			try {
				Offer o = db.generateOffer(contactId, maxMessages, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated offer: " + (o != null));
				if(o != null) writerTasks.add(new WriteOffer(o));
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
				LOG.info("Sent offer");
				dbExecutor.execute(new GenerateOffer());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
	private class GenerateRequest implements Runnable {

		public void run() {
			assert writer != null;
			int maxMessages = writer.getMaxMessagesForRequest(Long.MAX_VALUE);
			try {
				Request r = db.generateRequest(contactId, maxMessages);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated request: " + (r != null));
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
				LOG.info("Sent request");
				dbExecutor.execute(new GenerateRequest());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent retention ack");
				dbExecutor.execute(new GenerateRetentionAck());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent retention update");
				dbExecutor.execute(new GenerateRetentionUpdate());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent subscription ack");
				dbExecutor.execute(new GenerateSubscriptionAck());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent subscription update");
				dbExecutor.execute(new GenerateSubscriptionUpdate());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent transport acks");
				dbExecutor.execute(new GenerateTransportAcks());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}

	// This task runs on the database thread
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
				LOG.info("Sent transport updates");
				dbExecutor.execute(new GenerateTransportUpdates());
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				dispose(true, true);
			}
		}
	}
}
