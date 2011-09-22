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
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.batch.BatchTransportWriter;

class OutgoingBatchConnection {

	private final BatchTransportWriter trans;
	private final ConnectionWriterFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolWriterFactory protoFactory;
	private final int transportId;
	private final long connection;
	private final ContactId contactId;

	OutgoingBatchConnection(BatchTransportWriter trans,
			ConnectionWriterFactory connFactory, DatabaseComponent db,
			ProtocolWriterFactory protoFactory, int transportId,
			long connection, ContactId contactId) {
		this.trans = trans;
		this.connFactory = connFactory;
		this.db = db;
		this.protoFactory = protoFactory;
		this.transportId = transportId;
		this.connection = connection;
		this.contactId = contactId;
	}

	void write() throws DbException, IOException {
		byte[] secret = db.getSharedSecret(contactId);
		ConnectionWriter conn = connFactory.createConnectionWriter(
				trans.getOutputStream(), true, transportId, connection, secret);
		OutputStream out = conn.getOutputStream();
		// There should be enough space for a packet
		long capacity = conn.getCapacity(trans.getCapacity());
		if(capacity < MAX_PACKET_LENGTH) throw new IOException();
		// Write a transport update
		TransportWriter t = protoFactory.createTransportWriter(out);
		db.generateTransportUpdate(contactId, t);
		// If there's space, write a subscription update
		capacity = conn.getCapacity(trans.getCapacity());
		if(capacity >= MAX_PACKET_LENGTH) {
			SubscriptionWriter s = protoFactory.createSubscriptionWriter(out);
			db.generateSubscriptionUpdate(contactId, s);
		}
		// Write acks until you can't write acks no more
		AckWriter a = protoFactory.createAckWriter(out);
		do {
			capacity = conn.getCapacity(trans.getCapacity());
			int max = (int) Math.min(MAX_PACKET_LENGTH, capacity);
			a.setMaxPacketLength(max);
		} while(db.generateAck(contactId, a));
		// Write batches until you can't write batches no more
		BatchWriter b = protoFactory.createBatchWriter(out);
		do {
			capacity = conn.getCapacity(trans.getCapacity());
			int max = (int) Math.min(MAX_PACKET_LENGTH, capacity);
			b.setMaxPacketLength(max);
		} while(db.generateBatch(contactId, b));
		// Close the output stream
		out.close();
	}
}
