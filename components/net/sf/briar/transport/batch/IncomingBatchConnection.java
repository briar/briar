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
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;

class IncomingBatchConnection {

	private static final Logger LOG =
		Logger.getLogger(IncomingBatchConnection.class.getName());

	private final ConnectionReaderFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoFactory;
	private final ConnectionContext ctx;
	private final BatchTransportReader reader;
	private final byte[] encryptedIv;

	IncomingBatchConnection(ConnectionReaderFactory connFactory,
			DatabaseComponent db, ProtocolReaderFactory protoFactory,
			ConnectionContext ctx, BatchTransportReader reader,
			byte[] encryptedIv) {
		this.connFactory = connFactory;
		this.db = db;
		this.protoFactory = protoFactory;
		this.ctx = ctx;
		this.reader = reader;
		this.encryptedIv = encryptedIv;
	}

	void read() {
		try {
			ConnectionReader conn = connFactory.createConnectionReader(
					reader.getInputStream(), ctx, encryptedIv);
			ProtocolReader proto = protoFactory.createProtocolReader(
					conn.getInputStream());
			ContactId c = ctx.getContactId();
			// Read packets until EOF
			while(!proto.eof()) {
				if(proto.hasAck()) {
					Ack a = proto.readAck();
					db.receiveAck(c, a);
				} else if(proto.hasBatch()) {
					Batch b = proto.readBatch();
					db.receiveBatch(c, b);
				} else if(proto.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = proto.readSubscriptionUpdate();
					db.receiveSubscriptionUpdate(c, s);
				} else if(proto.hasTransportUpdate()) {
					TransportUpdate t = proto.readTransportUpdate();
					db.receiveTransportUpdate(c, t);
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
