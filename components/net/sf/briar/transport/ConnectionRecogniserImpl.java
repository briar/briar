package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionWindow;

import com.google.inject.Inject;

class ConnectionRecogniserImpl implements ConnectionRecogniser,
DatabaseListener {

	private static final Logger LOG =
		Logger.getLogger(ConnectionRecogniserImpl.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final Executor executor;
	private final ShutdownManager shutdown;
	private final Cipher ivCipher; // Locking: this
	private final Map<Bytes, Context> expected; // Locking: this

	private boolean initialised = false; // Locking: this

	@Inject
	ConnectionRecogniserImpl(CryptoComponent crypto, DatabaseComponent db,
			Executor executor, ShutdownManager shutdown) {
		this.crypto = crypto;
		this.db = db;
		this.executor = executor;
		this.shutdown = shutdown;
		ivCipher = crypto.getIvCipher();
		expected = new HashMap<Bytes, Context>();
		db.addListener(this);
	}

	// Locking: this
	private void initialise() throws DbException {
		assert !initialised;
		shutdown.addShutdownHook(new Runnable() {
			public void run() {
				eraseSecrets();
			}
		});
		Collection<TransportId> transports = new ArrayList<TransportId>();
		for(Transport t : db.getLocalTransports()) transports.add(t.getId());
		for(ContactId c : db.getContacts()) {
			Collection<Context> contexts = new ArrayList<Context>();
			try {
				for(TransportId t : transports) {
					TransportIndex i = db.getRemoteIndex(c, t);
					if(i == null) continue;
					ConnectionWindow w = db.getConnectionWindow(c, i);
					for(long unseen : w.getUnseen().keySet()) {
						contexts.add(new Context(c, t, i, unseen, w));
					}
				}
			} catch(NoSuchContactException e) {
				// The contact was removed - don't add the IVs
				for(Context ctx : contexts) ctx.window.erase();
				continue;
			}
			for(Context ctx : contexts) expected.put(calculateIv(ctx), ctx);
		}
		initialised = true;
	}

	private synchronized void eraseSecrets() {
		for(Context c : expected.values()) c.window.erase();
	}

	// Locking: this
	private Bytes calculateIv(Context ctx) {
		byte[] secret = ctx.window.getUnseen().get(ctx.connection);
		byte[] iv = encryptIv(ctx.transportIndex, ctx.connection, secret);
		return new Bytes(iv);
	}

	// Locking: this
	private byte[] encryptIv(TransportIndex i, long connection, byte[] secret) {
		byte[] iv = IvEncoder.encodeIv(true, i.getInt(), connection);
		ErasableKey ivKey = crypto.deriveIvKey(secret, true);
		try {
			ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
			return ivCipher.doFinal(iv);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		} finally {
			ivKey.erase();
		}
	}

	public void acceptConnection(final TransportId t, final byte[] encryptedIv,
			final Callback callback) {
		executor.execute(new Runnable() {
			public void run() {
				try {
					ConnectionContext ctx = acceptConnection(t, encryptedIv);
					if(ctx == null) callback.connectionRejected();
					else callback.connectionAccepted(ctx);
				} catch(DbException e) {
					callback.handleException(e);
				}
			}
		});
	}

	private ConnectionContext acceptConnection(TransportId t,
			byte[] encryptedIv) throws DbException {
		if(encryptedIv.length != IV_LENGTH)
			throw new IllegalArgumentException();
		synchronized(this) {
			if(!initialised) initialise();
			Bytes b = new Bytes(encryptedIv);
			Context ctx = expected.get(b);
			if(ctx == null || !ctx.transportId.equals(t)) return null;
			// The IV was expected
			expected.remove(b);
			ContactId c = ctx.contactId;
			TransportIndex i = ctx.transportIndex;
			long connection = ctx.connection;
			ConnectionWindow w = ctx.window;
			// Get the secret and update the connection window
			byte[] secret = w.setSeen(connection);
			try {
				db.setConnectionWindow(c, i, w);
			} catch(NoSuchContactException e) {
				// The contact was removed - reject the connection
				removeContact(c);
				w.erase();
				return null;
			}
			// Update the connection window's expected IVs
			Iterator<Context> it = expected.values().iterator();
			while(it.hasNext()) {
				Context ctx1 = it.next();
				if(ctx1.contactId.equals(c) && ctx1.transportIndex.equals(i))
					it.remove();
			}
			for(long unseen : w.getUnseen().keySet()) {
				Context ctx1 = new Context(c, t, i, unseen, w);
				expected.put(calculateIv(ctx1), ctx1);
			}
			return new ConnectionContextImpl(c, i, connection, secret);
		}
	}

	private synchronized void removeContact(ContactId c) {
		if(!initialised) return;
		Iterator<Context> it = expected.values().iterator();
		while(it.hasNext()) {
			Context ctx = it.next();
			if(ctx.contactId.equals(c)) {
				ctx.window.erase();
				it.remove();
			}
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			// Remove the expected IVs for the ex-contact
			final ContactId c = ((ContactRemovedEvent) e).getContactId();
			executor.execute(new Runnable() {
				public void run() {
					removeContact(c);
				}
			});
		} else if(e instanceof TransportAddedEvent) {
			// Add the expected IVs for the new transport
			final TransportId t = ((TransportAddedEvent) e).getTransportId();
			executor.execute(new Runnable() {
				public void run() {
					addTransport(t);
				}
			});
		} else if(e instanceof RemoteTransportsUpdatedEvent) {
			// Recalculate the expected IVs for the contact
			final ContactId c =
				((RemoteTransportsUpdatedEvent) e).getContactId();
			executor.execute(new Runnable() {
				public void run() {
					updateContact(c);
				}
			});
		}
	}

	private synchronized void addTransport(TransportId t) {
		if(!initialised) return;
		try {
			for(ContactId c : db.getContacts()) {
				Collection<Context> contexts = new ArrayList<Context>();
				try {
					TransportIndex i = db.getRemoteIndex(c, t);
					if(i == null) continue;
					ConnectionWindow w = db.getConnectionWindow(c, i);
					for(long unseen : w.getUnseen().keySet()) {
						contexts.add(new Context(c, t, i, unseen, w));
					}
				} catch(NoSuchContactException e) {
					// The contact was removed - don't add the IVs
					for(Context ctx : contexts) ctx.window.erase();
					continue;
				}
				for(Context ctx : contexts) expected.put(calculateIv(ctx), ctx);
			}
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	private synchronized void updateContact(ContactId c) {
		if(!initialised) return;
		removeContact(c);
		try {
			Collection<Context> contexts = new ArrayList<Context>();
			try {
				for(Transport transport : db.getLocalTransports()) {
					TransportId t = transport.getId();
					TransportIndex i = db.getRemoteIndex(c, t);
					ConnectionWindow w = db.getConnectionWindow(c, i);
					for(long unseen : w.getUnseen().keySet()) {
						contexts.add(new Context(c, t, i, unseen, w));
					}
				}
			} catch(NoSuchContactException e) {
				// The contact was removed - don't add the IVs
				return;
			}
			for(Context ctx : contexts) expected.put(calculateIv(ctx), ctx);
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	private static class Context {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportIndex transportIndex;
		private final long connection;
		// Locking: ConnectionRecogniser.this
		private final ConnectionWindow window;

		private Context(ContactId contactId, TransportId transportId,
				TransportIndex transportIndex, long connection,
				ConnectionWindow window) {
			this.contactId = contactId;
			this.transportId = transportId;
			this.transportIndex = transportIndex;
			this.connection = connection;
			this.window = window;
		}
	}
}
