package org.briarproject.messaging;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
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
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;

/**
 * An outgoing {@link org.briarproject.api.messaging.MessagingSession
 * MessagingSession} that keeps its output stream open and reacts to events
 * that make packets available to send.
 */
class ReactiveOutgoingSession implements MessagingSession, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ReactiveOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
		public void run() {}
	};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final StreamContext ctx;
	private final TransportConnectionWriter transportWriter;
	private final ContactId contactId;
	private final long maxLatency;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile PacketWriter packetWriter = null;
	private volatile boolean interrupted = false;

	ReactiveOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, StreamWriterFactory streamWriterFactory,
			PacketWriterFactory packetWriterFactory, StreamContext ctx,
			TransportConnectionWriter transportWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.streamWriterFactory = streamWriterFactory;
		this.packetWriterFactory = packetWriterFactory;
		this.ctx = ctx;
		this.transportWriter = transportWriter;
		contactId = ctx.getContactId();
		maxLatency = transportWriter.getMaxLatency();
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			OutputStream out = transportWriter.getOutputStream();
			int maxFrameLength = transportWriter.getMaxFrameLength();
			StreamWriter streamWriter = streamWriterFactory.createStreamWriter(
					out, maxFrameLength, ctx);
			out = streamWriter.getOutputStream();
			packetWriter = packetWriterFactory.createPacketWriter(out, true);
			// Start a query for each type of packet, in order of urgency
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
			// Write packets until interrupted
			try {
				while(!interrupted) {
					ThrowingRunnable<IOException> task = writerTasks.take();
					if(task == CLOSE) break;
					task.run();
				}
				out.flush();
				out.close();
			} catch(InterruptedException e) {
				LOG.info("Interrupted while waiting for a packet to write");
				Thread.currentThread().interrupt();
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	public void interrupt() {
		interrupted = true;
		writerTasks.add(CLOSE);
	}

	public void eventOccurred(Event e) {
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(contactId.equals(c.getContactId())) {
				LOG.info("Contact removed, closing");
				interrupt();
			}
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
		} else if(e instanceof TransportRemovedEvent) {
			TransportRemovedEvent t = (TransportRemovedEvent) e;
			if(ctx.getTransportId().equals(t.getTransportId())) {
				LOG.info("Transport removed, closing");
				interrupt();
			}
		}
	}

	// This task runs on the database thread
	private class GenerateAck implements Runnable {

		public void run() {
			int maxMessages = packetWriter.getMaxMessagesForAck(Long.MAX_VALUE);
			try {
				Ack a = db.generateAck(contactId, maxMessages);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
				if(a != null) writerTasks.add(new WriteAck(a));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteAck implements ThrowingRunnable<IOException> {

		private final Ack ack;

		private WriteAck(Ack ack) {
			this.ack = ack;
		}

		public void run() throws IOException {
			packetWriter.writeAck(ack);
			LOG.info("Sent ack");
			dbExecutor.execute(new GenerateAck());
		}
	}

	// This task runs on the database thread
	private class GenerateBatch implements Runnable {

		public void run() {
			try {
				Collection<byte[]> b = db.generateRequestedBatch(contactId,
						MAX_PACKET_LENGTH, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if(b != null) writerTasks.add(new WriteBatch(b));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteBatch implements ThrowingRunnable<IOException> {

		private final Collection<byte[]> batch;

		private WriteBatch(Collection<byte[]> batch) {
			this.batch = batch;
		}

		public void run() throws IOException {
			for(byte[] raw : batch) packetWriter.writeMessage(raw);
			LOG.info("Sent batch");
			dbExecutor.execute(new GenerateBatch());
		}
	}

	// This task runs on the database thread
	private class GenerateOffer implements Runnable {

		public void run() {
			int maxMessages = packetWriter.getMaxMessagesForOffer(
					Long.MAX_VALUE);
			try {
				Offer o = db.generateOffer(contactId, maxMessages, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated offer: " + (o != null));
				if(o != null) writerTasks.add(new WriteOffer(o));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteOffer implements ThrowingRunnable<IOException> {

		private final Offer offer;

		private WriteOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() throws IOException {
			packetWriter.writeOffer(offer);
			LOG.info("Sent offer");
			dbExecutor.execute(new GenerateOffer());
		}
	}

	// This task runs on the database thread
	private class GenerateRequest implements Runnable {

		public void run() {
			int maxMessages = packetWriter.getMaxMessagesForRequest(
					Long.MAX_VALUE);
			try {
				Request r = db.generateRequest(contactId, maxMessages);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated request: " + (r != null));
				if(r != null) writerTasks.add(new WriteRequest(r));
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteRequest implements ThrowingRunnable<IOException> {

		private final Request request;

		private WriteRequest(Request request) {
			this.request = request;
		}

		public void run() throws IOException {
			packetWriter.writeRequest(request);
			LOG.info("Sent request");
			dbExecutor.execute(new GenerateRequest());
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
				interrupt();
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteRetentionAck implements ThrowingRunnable<IOException> {

		private final RetentionAck ack;

		private WriteRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}


		public void run() throws IOException {
			packetWriter.writeRetentionAck(ack);
			LOG.info("Sent retention ack");
			dbExecutor.execute(new GenerateRetentionAck());
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
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteRetentionUpdate
	implements ThrowingRunnable<IOException> {

		private final RetentionUpdate update;

		private WriteRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() throws IOException {
			packetWriter.writeRetentionUpdate(update);
			LOG.info("Sent retention update");
			dbExecutor.execute(new GenerateRetentionUpdate());
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
				interrupt();
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteSubscriptionAck
	implements ThrowingRunnable<IOException> {

		private final SubscriptionAck ack;

		private WriteSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() throws IOException {
			packetWriter.writeSubscriptionAck(ack);
			LOG.info("Sent subscription ack");
			dbExecutor.execute(new GenerateSubscriptionAck());
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
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteSubscriptionUpdate
	implements ThrowingRunnable<IOException> {

		private final SubscriptionUpdate update;

		private WriteSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() throws IOException {
			packetWriter.writeSubscriptionUpdate(update);
			LOG.info("Sent subscription update");
			dbExecutor.execute(new GenerateSubscriptionUpdate());
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
				interrupt();
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteTransportAcks implements ThrowingRunnable<IOException> {

		private final Collection<TransportAck> acks;

		private WriteTransportAcks(Collection<TransportAck> acks) {
			this.acks = acks;
		}

		public void run() throws IOException {
			for(TransportAck a : acks) packetWriter.writeTransportAck(a);
			LOG.info("Sent transport acks");
			dbExecutor.execute(new GenerateTransportAcks());
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
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteTransportUpdates
	implements ThrowingRunnable<IOException> {

		private final Collection<TransportUpdate> updates;

		private WriteTransportUpdates(Collection<TransportUpdate> updates) {
			this.updates = updates;
		}

		public void run() throws IOException {
			for(TransportUpdate u : updates)
				packetWriter.writeTransportUpdate(u);
			LOG.info("Sent transport updates");
			dbExecutor.execute(new GenerateTransportUpdates());
		}
	}
}
