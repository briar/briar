package net.sf.briar.transport.batch;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
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

	private final Executor dbExecutor;
	private final ConnectionReaderFactory connFactory;
	private final DatabaseComponent db;
	private final ProtocolReaderFactory protoFactory;
	private final ConnectionContext ctx;
	private final BatchTransportReader reader;
	private final byte[] tag;

	IncomingBatchConnection(@DatabaseExecutor Executor dbExecutor,
			DatabaseComponent db, ConnectionReaderFactory connFactory,
			ProtocolReaderFactory protoFactory, ConnectionContext ctx,
			BatchTransportReader reader, byte[] tag) {
		this.dbExecutor = dbExecutor;
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
					Ack a = proto.readAck();
					dbExecutor.execute(new ReceiveAck(c, a));
				} else if(proto.hasBatch()) {
					UnverifiedBatch b = proto.readBatch();
					dbExecutor.execute(new ReceiveBatch(c, b));
				} else if(proto.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = proto.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(c, s));
				} else if(proto.hasTransportUpdate()) {
					TransportUpdate t = proto.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(c, t));
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

	private class ReceiveAck implements Runnable {

		private final ContactId contactId;
		private final Ack ack;

		private ReceiveAck(ContactId contactId, Ack ack) {
			this.contactId = contactId;
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveBatch implements Runnable {

		private final ContactId contactId;
		private final UnverifiedBatch batch;

		private ReceiveBatch(ContactId contactId, UnverifiedBatch batch) {
			this.contactId = contactId;
			this.batch = batch;
		}

		public void run() {
			try {
				// FIXME: Don't verify on the DB thread
				db.receiveBatch(contactId, batch.verify());
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveSubscriptionUpdate implements Runnable {

		private final ContactId contactId;
		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(ContactId contactId,
				SubscriptionUpdate update) {
			this.contactId = contactId;
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private class ReceiveTransportUpdate implements Runnable {

		private final ContactId contactId;
		private final TransportUpdate update;

		private ReceiveTransportUpdate(ContactId contactId,
				TransportUpdate update) {
			this.contactId = contactId;
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}
}
