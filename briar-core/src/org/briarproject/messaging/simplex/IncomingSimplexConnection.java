package org.briarproject.messaging.simplex;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.PacketReader;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.plugins.simplex.SimplexTransportReader;
import org.briarproject.api.transport.ConnectionContext;
import org.briarproject.api.transport.ConnectionReader;
import org.briarproject.api.transport.ConnectionReaderFactory;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.util.ByteUtils;

class IncomingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(IncomingSimplexConnection.class.getName());

	private final Executor dbExecutor, cryptoExecutor;
	private final MessageVerifier messageVerifier;
	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connReaderFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportReader transport;
	private final ContactId contactId;
	private final TransportId transportId;

	IncomingSimplexConnection(Executor dbExecutor, Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			PacketReaderFactory packetReaderFactory, ConnectionContext ctx,
			SimplexTransportReader transport) {
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
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
			InputStream in = transport.getInputStream();
			int maxFrameLength = transport.getMaxFrameLength();
			ConnectionReader conn = connReaderFactory.createConnectionReader(in,
					maxFrameLength, ctx, true, true);
			in = conn.getInputStream();
			PacketReader reader = packetReaderFactory.createPacketReader(in);
			// Read packets until EOF
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasMessage()) {
					UnverifiedMessage m = reader.readMessage();
					cryptoExecutor.execute(new VerifyMessage(m));
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
