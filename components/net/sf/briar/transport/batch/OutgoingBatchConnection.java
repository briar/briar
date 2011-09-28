package net.sf.briar.transport.batch;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionWriter;

class OutgoingBatchConnection {

	private final ConnectionWriter conn;
	private final DatabaseComponent db;
	private final ProtocolWriterFactory protoFactory;
	private final ContactId contactId;

	OutgoingBatchConnection(ConnectionWriter conn, DatabaseComponent db,
			ProtocolWriterFactory protoFactory, ContactId contactId) {
		this.conn = conn;
		this.db = db;
		this.protoFactory = protoFactory;
		this.contactId = contactId;
	}

	void write() throws DbException, IOException {
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
			SubscriptionWriter s = protoFactory.createSubscriptionWriter(out);
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
		// Close the output stream
		out.close();
	}
}
