package net.sf.briar.messaging.simplex;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReader;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.util.ByteUtils;

class IncomingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(IncomingSimplexConnection.class.getName());

	private final Executor dbExecutor, verificationExecutor;
	private final MessageVerifier messageVerifier;
	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connReaderFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportReader transport;
	private final ContactId contactId;
	private final TransportId transportId;

	IncomingSimplexConnection(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor verificationExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			PacketReaderFactory packetReaderFactory, ConnectionContext ctx,
			SimplexTransportReader transport) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.messageVerifier = messageVerifier;
		this.db = db;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.packetReaderFactory = packetReaderFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
	}

	void read() {
		connRegistry.registerConnection(contactId, transportId);
		try {
			ConnectionReader conn = connReaderFactory.createConnectionReader(
					transport.getInputStream(), ctx, true, true);
			InputStream in = conn.getInputStream();
			PacketReader reader = packetReaderFactory.createPacketReader(in);
			// Read packets until EOF
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasMessage()) {
					UnverifiedMessage m = reader.readMessage();
					verificationExecutor.execute(new VerifyMessage(m));
				} else if(reader.hasRetentionAck()) {
					RetentionAck a = reader.readRetentionAck();
					dbExecutor.execute(new ReceiveRetentionAck(a));
				} else if(reader.hasRetentionUpdate()) {
					RetentionUpdate u = reader.readRetentionUpdate();
					dbExecutor.execute(new ReceiveRetentionUpdate(u));
				} else if(reader.hasSubscriptionAck()) {
					SubscriptionAck a = reader.readSubscriptionAck();
					dbExecutor.execute(new ReceiveSubscriptionAck(a));
				} else if(reader.hasSubscriptionUpdate()) {
					SubscriptionUpdate u = reader.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(u));
				} else if(reader.hasTransportAck()) {
					TransportAck a = reader.readTransportAck();
					dbExecutor.execute(new ReceiveTransportAck(a));
				} else if(reader.hasTransportUpdate()) {
					TransportUpdate u = reader.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(u));
				} else {
					throw new FormatException();
				}
			}
			dispose(false, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		} finally {
			connRegistry.unregisterConnection(contactId, transportId);
		}
	}

	private void dispose(boolean exception, boolean recognised) {
		ByteUtils.erase(ctx.getSecret());
		try {
			transport.dispose(exception, recognised);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class VerifyMessage implements Runnable {

		private final UnverifiedMessage message;

		private VerifyMessage(UnverifiedMessage message) {
			this.message = message;
		}

		public void run() {
			try {
				Message m = messageVerifier.verifyMessage(message);
				dbExecutor.execute(new ReceiveMessage(m));
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveMessage implements Runnable {

		private final Message message;

		private ReceiveMessage(Message message) {
			this.message = message;
		}

		public void run() {
			try {
				db.receiveMessage(contactId, message);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveRetentionAck implements Runnable {

		private final RetentionAck ack;

		private ReceiveRetentionAck(RetentionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveRetentionAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveRetentionUpdate implements Runnable {

		private final RetentionUpdate update;

		private ReceiveRetentionUpdate(RetentionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveRetentionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveSubscriptionAck implements Runnable {

		private final SubscriptionAck ack;

		private ReceiveSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveSubscriptionAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveTransportAck implements Runnable {

		private final TransportAck ack;

		private ReceiveTransportAck(TransportAck ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveTransportAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private ReceiveTransportUpdate(TransportUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
