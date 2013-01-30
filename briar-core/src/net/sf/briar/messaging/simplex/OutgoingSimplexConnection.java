package net.sf.briar.messaging.simplex;

import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

class OutgoingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(OutgoingSimplexConnection.class.getName());

	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionWriterFactory connFactory;
	private final PacketWriterFactory protoFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportWriter transport;
	private final ContactId contactId;
	private final TransportId transportId;

	OutgoingSimplexConnection(DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionWriterFactory connFactory,
			PacketWriterFactory protoFactory, ConnectionContext ctx,
			SimplexTransportWriter transport) {
		this.db = db;
		this.connRegistry = connRegistry;
		this.connFactory = connFactory;
		this.protoFactory = protoFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
	}

	// FIXME: Write each packet to a buffer, check for capacity before writing
	// it to the connection (except raw messages, which are already serialised)
	void write() {
		connRegistry.registerConnection(contactId, transportId);
		try {
			ConnectionWriter conn = connFactory.createConnectionWriter(
					transport.getOutputStream(), transport.getCapacity(),
					ctx, false, true);
			OutputStream out = conn.getOutputStream();
			if(conn.getRemainingCapacity() < MAX_PACKET_LENGTH)
				throw new EOFException();
			PacketWriter writer = protoFactory.createPacketWriter(out,
					transport.shouldFlush());
			// Send the initial packets: updates and acks
			Collection<TransportAck> transportAcks =
					db.generateTransportAcks(contactId);
			if(transportAcks != null) {
				for(TransportAck ta : transportAcks)
					writer.writeTransportAck(ta);
			}
			Collection<TransportUpdate> transportUpdates =
					db.generateTransportUpdates(contactId);
			if(transportUpdates != null) {
				for(TransportUpdate tu : transportUpdates)
					writer.writeTransportUpdate(tu);
			}
			SubscriptionAck sa = db.generateSubscriptionAck(contactId);
			if(sa != null) writer.writeSubscriptionAck(sa);
			SubscriptionUpdate su = db.generateSubscriptionUpdate(contactId);
			if(su != null) writer.writeSubscriptionUpdate(su);
			RetentionAck ra = db.generateRetentionAck(contactId);
			if(ra != null) writer.writeRetentionAck(ra);
			RetentionUpdate ru = db.generateRetentionUpdate(contactId);
			if(ru != null) writer.writeRetentionUpdate(ru);
			// Write acks until you can't write acks no more
			long capacity = conn.getRemainingCapacity();
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
			Collection<byte[]> batch = db.generateBatch(contactId, maxLength);
			while(batch != null) {
				for(byte[] raw : batch) writer.writeMessage(raw);
				capacity = conn.getRemainingCapacity();
				maxLength = (int) Math.min(capacity, MAX_PACKET_LENGTH);
				batch = db.generateBatch(contactId, maxLength);
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
		} finally {
			connRegistry.unregisterConnection(contactId, transportId);
		}
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
