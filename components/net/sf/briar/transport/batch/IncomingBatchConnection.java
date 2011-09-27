package net.sf.briar.transport.batch;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ConnectionReader;

class IncomingBatchConnection {

	private final ConnectionReader conn;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoFactory;
	private final ContactId contactId;

	IncomingBatchConnection(ConnectionReader conn, DatabaseComponent db,
			ProtocolReaderFactory protoFactory, ContactId contactId) {
		this.conn = conn;
		this.db = db;
		this.protoFactory = protoFactory;
		this.contactId = contactId;
	}

	void read() throws DbException, IOException {
		InputStream in = conn.getInputStream();
		ProtocolReader proto = protoFactory.createProtocolReader(in);
		// Read packets until EOF
		while(!proto.eof()) {
			if(proto.hasAck()) {
				Ack a = proto.readAck();
				db.receiveAck(contactId, a);
			} else if(proto.hasBatch()) {
				Batch b = proto.readBatch();
				db.receiveBatch(contactId, b);
			} else if(proto.hasSubscriptionUpdate()) {
				SubscriptionUpdate s = proto.readSubscriptionUpdate();
				db.receiveSubscriptionUpdate(contactId, s);
			} else if(proto.hasTransportUpdate()) {
				TransportUpdate t = proto.readTransportUpdate();
				db.receiveTransportUpdate(contactId, t);
			} else {
				throw new FormatException();
			}
		}
		// Close the input stream
		in.close();
	}
}
