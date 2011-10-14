package net.sf.briar.transport.batch;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;

class IncomingBatchConnection implements Runnable {

	private static final Logger LOG =
		Logger.getLogger(IncomingBatchConnection.class.getName());

	private final ConnectionReaderFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoFactory;
	private final ContactId contactId;
	private final BatchTransportReader reader;
	private final byte[] encryptedIv;

	IncomingBatchConnection(ConnectionReaderFactory connFactory,
			DatabaseComponent db, ProtocolReaderFactory protoFactory,
			ContactId contactId, BatchTransportReader reader,
			byte[] encryptedIv) {
		this.connFactory = connFactory;
		this.db = db;
		this.protoFactory = protoFactory;
		this.contactId = contactId;
		this.reader = reader;
		this.encryptedIv = encryptedIv;
	}

	public void run() {
		try {
			byte[] secret = db.getSharedSecret(contactId);
			ConnectionReader conn = connFactory.createConnectionReader(
					reader.getInputStream(), encryptedIv, secret);
			ProtocolReader proto = protoFactory.createProtocolReader(
					conn.getInputStream());
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
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			reader.dispose(false);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			reader.dispose(false);
		}
		// Success
		reader.dispose(true);
	}
}
