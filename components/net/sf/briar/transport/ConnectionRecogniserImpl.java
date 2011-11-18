package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionRecogniserImpl implements ConnectionRecogniser,
DatabaseListener {

	private static final Logger LOG =
		Logger.getLogger(ConnectionRecogniserImpl.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final Executor executor;
	private final Cipher ivCipher; // Locking: this
	private final Map<Bytes, Context> expected; // Locking: this
	private final AtomicBoolean initialised = new AtomicBoolean(false);

	@Inject
	ConnectionRecogniserImpl(CryptoComponent crypto, DatabaseComponent db,
			Executor executor) {
		this.crypto = crypto;
		this.db = db;
		this.executor = executor;
		ivCipher = crypto.getIvCipher();
		expected = new HashMap<Bytes, Context>();
		db.addListener(this);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				eraseSecrets();
			}
		});
	}

	private synchronized void eraseSecrets() {
		for(Context c : expected.values()) {
			for(byte[] b : c.window.getUnseen().values()) ByteUtils.erase(b);
		}
	}

	private void initialise() throws DbException {
		// Fill in the contexts as far as possible outside the lock
		Collection<Context> partial = new ArrayList<Context>();
		Collection<Transport> transports = db.getLocalTransports();
		for(ContactId c : db.getContacts()) {
			for(Transport transport : transports) {
				getPartialContexts(c, transport.getId(), partial);
			}
		}
		synchronized(this) {
			// Complete the contexts and calculate the expected IVs
			calculateIvs(completeContexts(partial));
		}
	}

	private void getPartialContexts(ContactId c, TransportId t,
			Collection<Context> partial) throws DbException {
		try {
			TransportIndex i = db.getRemoteIndex(c, t);
			if(i != null) {
				// Acquire the lock to avoid getting stale data
				synchronized(this) {
					ConnectionWindow w = db.getConnectionWindow(c, i);
					partial.add(new Context(c, t, i, -1, w));
				}
			}
		} catch(NoSuchContactException e) {
			// The contact was removed - we'll handle the event later
		}
	}

	// Locking: this
	private Collection<Context> completeContexts(Collection<Context> partial) {
		Collection<Context> contexts = new ArrayList<Context>();
		for(Context ctx : partial) {
			for(long unseen : ctx.window.getUnseen().keySet()) {
				contexts.add(new Context(ctx.contactId, ctx.transportId,
						ctx.transportIndex, unseen, ctx.window));
			}
		}
		return contexts;
	}

	// Locking: this
	private void calculateIvs(Collection<Context> contexts) {
		for(Context ctx : contexts) {
			byte[] secret = ctx.window.getUnseen().get(ctx.connection);
			byte[] iv = encryptIv(ctx.transportIndex, ctx.connection, secret);
			expected.put(new Bytes(iv), ctx);
		}
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
		if(!initialised.getAndSet(true)) initialise();
		synchronized(this) {
			Bytes b = new Bytes(encryptedIv);
			Context ctx = expected.get(b);
			if(ctx == null || !ctx.transportId.equals(t)) return null;
			// The IV was expected
			expected.remove(b);
			ContactId c = ctx.contactId;
			TransportIndex i = ctx.transportIndex;
			long connection = ctx.connection;
			ConnectionWindow w = ctx.window;
			byte[] secret;
			// Get the secret and update the connection window
			try {
				db.setConnectionWindow(c, i, w);
			} catch(NoSuchContactException e) {
				// The contact was removed - we'll handle the event later
			}
			secret = w.setSeen(connection);
			// Update the connection window's expected IVs
			Iterator<Context> it = expected.values().iterator();
			while(it.hasNext()) {
				Context ctx1 = it.next();
				if(ctx1.contactId.equals(c)
						&& ctx1.transportIndex.equals(i)) it.remove();
			}
			Collection<Context> contexts = new ArrayList<Context>();
			for(long unseen : w.getUnseen().keySet()) {
				contexts.add(new Context(c, t, i, unseen, w));
			}
			calculateIvs(contexts);
			return new ConnectionContextImpl(c, i, connection, secret);
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			// Remove the expected IVs for the ex-contact
			final ContactId c = ((ContactRemovedEvent) e).getContactId();
			executor.execute(new Runnable() {
				public void run() {
					removeIvs(c);
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

	private synchronized void removeIvs(ContactId c) {
		Iterator<Context> it = expected.values().iterator();
		while(it.hasNext()) if(it.next().contactId.equals(c)) it.remove();
	}

	private void addTransport(TransportId t) {
		// Fill in the contexts as far as possible outside the lock
		Collection<Context> partial = new ArrayList<Context>();
		try {
			for(ContactId c : db.getContacts()) {
				getPartialContexts(c, t, partial);
			}
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return;
		}
		synchronized(this) {
			// Complete the contexts and calculate the expected IVs
			calculateIvs(completeContexts(partial));
		}
	}

	private void updateContact(ContactId c) {
		// Fill in the contexts as far as possible outside the lock
		Collection<Context> partial = new ArrayList<Context>();
		try {
			Collection<Transport> transports = db.getLocalTransports();
			for(Transport transport : transports) {
				getPartialContexts(c, transport.getId(), partial);
			}
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return;
		}
		synchronized(this) {
			// Clear the contact's existing IVs
			Iterator<Context> it = expected.values().iterator();
			while(it.hasNext()) if(it.next().contactId.equals(c)) it.remove();
			// Complete the contexts and calculate the expected IVs
			calculateIvs(completeContexts(partial));
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
