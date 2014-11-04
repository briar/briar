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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
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
 * MessagingSession} that closes its output stream when no more packets are
 * available to send.
 */
class SinglePassOutgoingSession implements MessagingSession {

	private static final Logger LOG =
			Logger.getLogger(SinglePassOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
		public void run() {}
	};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final StreamContext ctx;
	private final TransportConnectionWriter transportWriter;
	private final ContactId contactId;
	private final long maxLatency;
	private final AtomicInteger outstandingQueries;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile StreamWriter streamWriter = null;
	private volatile PacketWriter packetWriter = null;
	private volatile boolean interrupted = false;

	SinglePassOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			StreamWriterFactory streamWriterFactory,
			PacketWriterFactory packetWriterFactory, StreamContext ctx,
			TransportConnectionWriter transportWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.streamWriterFactory = streamWriterFactory;
		this.packetWriterFactory = packetWriterFactory;
		this.ctx = ctx;
		this.transportWriter = transportWriter;
		contactId = ctx.getContactId();
		maxLatency = transportWriter.getMaxLatency();
		outstandingQueries = new AtomicInteger(8); // One per type of packet
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	public void run() throws IOException {
		OutputStream out = transportWriter.getOutputStream();
		int maxFrameLength = transportWriter.getMaxFrameLength();
		streamWriter = streamWriterFactory.createStreamWriter(out,
				maxFrameLength, ctx);
		out = streamWriter.getOutputStream();
		packetWriter = packetWriterFactory.createPacketWriter(out, false);
		// Start a query for each type of packet, in order of urgency
		dbExecutor.execute(new GenerateTransportAcks());
		dbExecutor.execute(new GenerateTransportUpdates());
		dbExecutor.execute(new GenerateSubscriptionAck());
		dbExecutor.execute(new GenerateSubscriptionUpdate());
		dbExecutor.execute(new GenerateRetentionAck());
		dbExecutor.execute(new GenerateRetentionUpdate());
		dbExecutor.execute(new GenerateAck());
		dbExecutor.execute(new GenerateBatch());
		// Write packets until interrupted or there are no more packets to write
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
	}

	public void interrupt() {
		interrupted = true;
		writerTasks.add(CLOSE);
	}

	private void decrementOutstandingQueries() {
		if(outstandingQueries.decrementAndGet() == 0) writerTasks.add(CLOSE);
	}

	// This task runs on the database thread
	private class GenerateAck implements Runnable {

		public void run() {
			int maxMessages = packetWriter.getMaxMessagesForAck(Long.MAX_VALUE);
			try {
				Ack a = db.generateAck(contactId, maxMessages);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
				if(a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteAck(a));
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
				Collection<byte[]> b = db.generateBatch(contactId,
						MAX_PACKET_LENGTH, maxLatency);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if(b == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteBatch(b));
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
	private class GenerateRetentionAck implements Runnable {

		public void run() {
			try {
				RetentionAck a = db.generateRetentionAck(contactId);
				if(LOG.isLoggable(INFO))
					LOG.info("Generated retention ack: " + (a != null));
				if(a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteRetentionAck(a));
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
				if(u == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteRetentionUpdate(u));
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
				if(a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteSubscriptionAck(a));
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
				if(u == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteSubscriptionUpdate(u));
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
				if(acks == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteTransportAcks(acks));
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
				if(t == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteTransportUpdates(t));
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
