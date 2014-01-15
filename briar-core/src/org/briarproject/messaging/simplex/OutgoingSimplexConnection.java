package org.briarproject.messaging.simplex;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.plugins.simplex.SimplexTransportWriter;
import org.briarproject.api.transport.ConnectionContext;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.ConnectionWriter;
import org.briarproject.api.transport.ConnectionWriterFactory;
import org.briarproject.util.ByteUtils;

class OutgoingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(OutgoingSimplexConnection.class.getName());

	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionWriterFactory connWriterFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportWriter transport;
	private final ContactId contactId;
	private final TransportId transportId;
	private final long maxLatency;

	OutgoingSimplexConnection(DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionWriterFactory connWriterFactory,
			PacketWriterFactory packetWriterFactory, ConnectionContext ctx,
			SimplexTransportWriter transport) {
		this.db = db;
		this.connRegistry = connRegistry;
		this.connWriterFactory = connWriterFactory;
		this.packetWriterFactory = packetWriterFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
		maxLatency = transport.getMaxLatency();
	}

	void write() {
		connRegistry.registerConnection(contactId, transportId);
		try {
			OutputStream out = transport.getOutputStream();
			long capacity = transport.getCapacity();
			int maxFrameLength = transport.getMaxFrameLength();
			ConnectionWriter conn = connWriterFactory.createConnectionWriter(
					out, maxFrameLength, capacity, ctx, false, true);
			out = conn.getOutputStream();
			if(conn.getRemainingCapacity() < MAX_PACKET_LENGTH)
				throw new EOFException();
			PacketWriter writer = packetWriterFactory.createPacketWriter(out,
					false);
			// Send the initial packets: updates and acks
			boolean hasSpace = writeTransportAcks(conn, writer);
			if(hasSpace) hasSpace = writeTransportUpdates(conn, writer);
			if(hasSpace) hasSpace = writeSubscriptionAck(conn, writer);
			if(hasSpace) hasSpace = writeSubscriptionUpdate(conn, writer);
			if(hasSpace) hasSpace = writeRetentionAck(conn, writer);
			if(hasSpace) hasSpace = writeRetentionUpdate(conn, writer);
			// Write acks until you can't write acks no more
			capacity = conn.getRemainingCapacity();
			int maxMessages = writer.getMaxMessagesForAck(capacity);
			Ack a = db.generateAck(contactId, maxMessages);
			while(a != null) {
				writer.writeAck(a);
				capacity = conn.getRemainingCapacity();
				maxMessages = writer.getMaxMessagesForAck(capacity);
				a = db.generateAck(contactId, maxMessages);
			}
			// Write messages until you can't write messages no more
			capacity = conn.getRemainingCapacity();
			int maxLength = (int) Math.min(capacity, MAX_PACKET_LENGTH);
			Collection<byte[]> batch = db.generateBatch(contactId, maxLength,
					maxLatency);
			while(batch != null) {
				for(byte[] raw : batch) writer.writeMessage(raw);
				capacity = conn.getRemainingCapacity();
				maxLength = (int) Math.min(capacity, MAX_PACKET_LENGTH);
				batch = db.generateBatch(contactId, maxLength, maxLatency);
			}
			writer.flush();
			writer.close();
			dispose(false);
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true);
		}
		connRegistry.unregisterConnection(contactId, transportId);
	}

	private boolean writeTransportAcks(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		Collection<TransportAck> acks = db.generateTransportAcks(contactId);
		if(acks == null) return true;
		for(TransportAck a : acks) {
			writer.writeTransportAck(a);
			if(conn.getRemainingCapacity() < MAX_PACKET_LENGTH) return false;
		}
		return true;
	}

	private boolean writeTransportUpdates(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		Collection<TransportUpdate> updates =
				db.generateTransportUpdates(contactId, maxLatency);
		if(updates == null) return true;
		for(TransportUpdate u : updates) {
			writer.writeTransportUpdate(u);
			if(conn.getRemainingCapacity() < MAX_PACKET_LENGTH) return false;
		}
		return true;
	}

	private boolean writeSubscriptionAck(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		SubscriptionAck a = db.generateSubscriptionAck(contactId);
		if(a == null) return true;
		writer.writeSubscriptionAck(a);
		return conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
	}

	private boolean writeSubscriptionUpdate(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		SubscriptionUpdate u =
				db.generateSubscriptionUpdate(contactId, maxLatency);
		if(u == null) return true;
		writer.writeSubscriptionUpdate(u);
		return conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
	}

	private boolean writeRetentionAck(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		RetentionAck a = db.generateRetentionAck(contactId);
		if(a == null) return true;
		writer.writeRetentionAck(a);
		return conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
	}

	private boolean writeRetentionUpdate(ConnectionWriter conn,
			PacketWriter writer) throws DbException, IOException {
		assert conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
		RetentionUpdate u = db.generateRetentionUpdate(contactId, maxLatency);
		if(u == null) return true;
		writer.writeRetentionUpdate(u);
		return conn.getRemainingCapacity() >= MAX_PACKET_LENGTH;
	}

	private void dispose(boolean exception) {
		ByteUtils.erase(ctx.getSecret());
		try {
			transport.dispose(exception);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
