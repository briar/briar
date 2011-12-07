package net.sf.briar.transport.batch;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class OutgoingBatchConnection {

	private static final Logger LOG =
		Logger.getLogger(OutgoingBatchConnection.class.getName());

	private final DatabaseComponent db;
	private final ConnectionWriterFactory connFactory;
	private final ProtocolWriterFactory protoFactory;
	private final ContactId contactId;
	private final TransportIndex transportIndex;
	private final BatchTransportWriter transport;

	OutgoingBatchConnection(DatabaseComponent db,
			ConnectionWriterFactory connFactory,
			ProtocolWriterFactory protoFactory, ContactId contactId,
			TransportIndex transportIndex, BatchTransportWriter transport) {
		this.db = db;
		this.connFactory = connFactory;
		this.protoFactory = protoFactory;
		this.contactId = contactId;
		this.transportIndex = transportIndex;
		this.transport = transport;
	}

	void write() {
		try {
			ConnectionContext ctx = db.getConnectionContext(contactId,
					transportIndex);
			ConnectionWriter conn = connFactory.createConnectionWriter(
					transport.getOutputStream(), transport.getCapacity(),
					ctx.getSecret());
			OutputStream out = conn.getOutputStream();
			ProtocolWriter proto = protoFactory.createProtocolWriter(out);
			// There should be enough space for a packet
			long capacity = conn.getRemainingCapacity();
			if(capacity < MAX_PACKET_LENGTH) throw new IOException();
			// Write a transport update
			TransportUpdate t = db.generateTransportUpdate(contactId);
			if(t != null) proto.writeTransportUpdate(t);
			// If there's space, write a subscription update
			capacity = conn.getRemainingCapacity();
			if(capacity >= MAX_PACKET_LENGTH) {
				SubscriptionUpdate s = db.generateSubscriptionUpdate(contactId);
				if(s != null) proto.writeSubscriptionUpdate(s);
			}
			// Write acks until you can't write acks no more
			capacity = conn.getRemainingCapacity();
			int maxBatches = proto.getMaxBatchesForAck(capacity);
			Ack a = db.generateAck(contactId, maxBatches);
			while(a != null) {
				proto.writeAck(a);
				capacity = conn.getRemainingCapacity();
				maxBatches = proto.getMaxBatchesForAck(capacity);
				a = db.generateAck(contactId, maxBatches);
			}
			// Write batches until you can't write batches no more
			capacity = conn.getRemainingCapacity();
			capacity = proto.getMessageCapacityForBatch(capacity);
			RawBatch b = db.generateBatch(contactId, (int) capacity);
			while(b != null) {
				proto.writeBatch(b);
				capacity = conn.getRemainingCapacity();
				capacity = proto.getMessageCapacityForBatch(capacity);
				b = db.generateBatch(contactId, (int) capacity);
			}
			// Flush the output stream
			out.flush();
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			transport.dispose(false);
		}
		// Success
		transport.dispose(true);
	}
}
