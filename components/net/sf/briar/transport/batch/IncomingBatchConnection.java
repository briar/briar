package net.sf.briar.transport.batch;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;

class IncomingBatchConnection {

	private static final Logger LOG =
		Logger.getLogger(IncomingBatchConnection.class.getName());

	private final Executor executor;
	private final ConnectionReaderFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoFactory;
	private final ConnectionContext ctx;
	private final BatchTransportReader reader;
	private final byte[] tag;

	IncomingBatchConnection(Executor executor,
			ConnectionReaderFactory connFactory,
			DatabaseComponent db, ProtocolReaderFactory protoFactory,
			ConnectionContext ctx, BatchTransportReader reader, byte[] tag) {
		this.executor = executor;
		this.connFactory = connFactory;
		this.db = db;
		this.protoFactory = protoFactory;
		this.ctx = ctx;
		this.reader = reader;
		this.tag = tag;
	}

	void read() {
		try {
			ConnectionReader conn = connFactory.createConnectionReader(
					reader.getInputStream(), ctx.getSecret(), tag);
			ProtocolReader proto = protoFactory.createProtocolReader(
					conn.getInputStream());
			final ContactId c = ctx.getContactId();
			// Read packets until EOF
			while(!proto.eof()) {
				if(proto.hasAck()) {
					final Ack a = proto.readAck();
					// Store the ack on another thread
					executor.execute(new Runnable() {
						public void run() {
							try {
								db.receiveAck(c, a);
							} catch(DbException e) {
								if(LOG.isLoggable(Level.WARNING))
									LOG.warning(e.getMessage());
							}
						}
					});
				} else if(proto.hasBatch()) {
					final UnverifiedBatch b = proto.readBatch();
					// Verify and store the batch on another thread
					executor.execute(new Runnable() {
						public void run() {
							try {
								db.receiveBatch(c, b.verify());
							} catch(DbException e) {
								if(LOG.isLoggable(Level.WARNING))
									LOG.warning(e.getMessage());
							} catch(GeneralSecurityException e) {
								if(LOG.isLoggable(Level.WARNING))
									LOG.warning(e.getMessage());
							}
						}
					});
				} else if(proto.hasSubscriptionUpdate()) {
					final SubscriptionUpdate s = proto.readSubscriptionUpdate();
					// Store the update on another thread
					executor.execute(new Runnable() {
						public void run() {
							try {
								db.receiveSubscriptionUpdate(c, s);
							} catch(DbException e) {
								if(LOG.isLoggable(Level.WARNING))
									LOG.warning(e.getMessage());
							}
						}
					});
				} else if(proto.hasTransportUpdate()) {
					final TransportUpdate t = proto.readTransportUpdate();
					// Store the update on another thread
					executor.execute(new Runnable() {
						public void run() {
							try {
								db.receiveTransportUpdate(c, t);
							} catch(DbException e) {
								if(LOG.isLoggable(Level.WARNING))
									LOG.warning(e.getMessage());
							}
						}
					});
				} else {
					throw new FormatException();
				}
			}
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			reader.dispose(false);
		}
		// Success
		reader.dispose(true);
	}
}
