package net.sf.briar.transport.batch;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class OutgoingBatchConnection {

	private static final Logger LOG =
		Logger.getLogger(OutgoingBatchConnection.class.getName());

	private final ConnectionWriterFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolWriterFactory protoFactory;
	private final TransportId transportId;
	private final ContactId contactId;
	private final BatchTransportWriter writer;

	OutgoingBatchConnection(ConnectionWriterFactory connFactory,
			DatabaseComponent db, ProtocolWriterFactory protoFactory,
			TransportId transportId, ContactId contactId,
			BatchTransportWriter writer) {
		this.connFactory = connFactory;
		this.db = db;
		this.protoFactory = protoFactory;
		this.transportId = transportId;
		this.contactId = contactId;
		this.writer = writer;
	}

	void write() {
		try {
			byte[] secret = db.getSharedSecret(contactId);
			long connection = db.getConnectionNumber(contactId, transportId);
			ConnectionWriter conn = connFactory.createConnectionWriter(
					writer.getOutputStream(), writer.getCapacity(), transportId,
					connection, secret);
			OutputStream out = conn.getOutputStream();
			// There should be enough space for a packet
			long capacity = conn.getRemainingCapacity();
			if(capacity < MAX_PACKET_LENGTH) throw new IOException();
			// Write a transport update
			TransportWriter t = protoFactory.createTransportWriter(out);
			db.generateTransportUpdate(contactId, t);
			// If there's space, write a subscription update
			capacity = conn.getRemainingCapacity();
			if(capacity >= MAX_PACKET_LENGTH) {
				SubscriptionWriter s =
					protoFactory.createSubscriptionWriter(out);
				db.generateSubscriptionUpdate(contactId, s);
			}
			// Write acks until you can't write acks no more
			AckWriter a = protoFactory.createAckWriter(out);
			do {
				capacity = conn.getRemainingCapacity();
				int max = (int) Math.min(MAX_PACKET_LENGTH, capacity);
				a.setMaxPacketLength(max);
			} while(db.generateAck(contactId, a));
			// Write batches until you can't write batches no more
			BatchWriter b = protoFactory.createBatchWriter(out);
			do {
				capacity = conn.getRemainingCapacity();
				int max = (int) Math.min(MAX_PACKET_LENGTH, capacity);
				b.setMaxPacketLength(max);
			} while(db.generateBatch(contactId, b));
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			writer.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			writer.dispose(false);
		}
		// Success
		writer.dispose(true);
	}
}
