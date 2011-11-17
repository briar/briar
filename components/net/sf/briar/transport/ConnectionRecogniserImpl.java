package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
	private final Map<Bytes, Context> expected; // Locking: this
	private final Collection<TransportId> localTransportIds; // Locking: this
	private final AtomicBoolean initialised = new AtomicBoolean(false);

	@Inject
	ConnectionRecogniserImpl(CryptoComponent crypto, DatabaseComponent db,
			Executor executor) {
		this.crypto = crypto;
		this.db = db;
		this.executor = executor;
		expected = new HashMap<Bytes, Context>();
		localTransportIds = new ArrayList<TransportId>();
		db.addListener(this);
	}

	private void initialise() throws DbException {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				eraseSecrets();
			}
		});
		Collection<TransportId> ids = new ArrayList<TransportId>();
		for(Transport t : db.getLocalTransports()) ids.add(t.getId());
		synchronized(this) {
			localTransportIds.addAll(ids);
		}
		Map<Bytes, Context> ivs = new HashMap<Bytes, Context>();
		for(ContactId c : db.getContacts()) {
			try {
				ivs.putAll(calculateIvs(c));
			} catch(NoSuchContactException e) {
				// The contact was removed - clean up in eventOccurred()
			}
		}
		synchronized(this) {
			expected.putAll(ivs);
		}
	}

	private synchronized void eraseSecrets() {
		for(Context c : expected.values()) {
			synchronized(c.window) {
				for(byte[] b : c.window.getUnseen().values()) {
					ByteUtils.erase(b);
				}
			}
		}
	}

	private Map<Bytes, Context> calculateIvs(ContactId c) throws DbException {
		Map<Bytes, Context> ivs = new HashMap<Bytes, Context>();
		Collection<TransportId> ids;
		synchronized(this) {
			ids = new ArrayList<TransportId>(localTransportIds);
		}
		for(TransportId t : ids) {
			TransportIndex i = db.getRemoteIndex(c, t);
			if(i != null) {
				ConnectionWindow w = db.getConnectionWindow(c, i);
				ivs.putAll(calculateIvs(c, t, i, w));
			}
		}
		return ivs;
	}

	private Map<Bytes, Context> calculateIvs(ContactId c, TransportId t,
			TransportIndex i, ConnectionWindow w) throws DbException {
		Map<Bytes, Context> ivs = new HashMap<Bytes, Context>();
		synchronized(w) {
			for(Entry<Long, byte[]> e : w.getUnseen().entrySet()) {
				long connection = e.getKey();
				byte[] secret = e.getValue();
				Bytes iv = new Bytes(encryptIv(i, connection, secret));
				ivs.put(iv, new Context(c, t, i, connection, w));
			}
		}
		return ivs;
	}

	private byte[] encryptIv(TransportIndex i, long connection, byte[] secret) {
		byte[] iv = IvEncoder.encodeIv(true, i.getInt(), connection);
		ErasableKey ivKey = crypto.deriveIvKey(secret, true);
		try {
			Cipher ivCipher = crypto.getIvCipher();
			ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
			byte[] encryptedIv = ivCipher.doFinal(iv);
			ivKey.erase();
			return encryptedIv;
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
	}

	public void acceptConnection(final TransportId t, final byte[] encryptedIv,
			final Callback callback) {
		executor.execute(new Runnable() {
			public void run() {
				acceptConnectionSync(t, encryptedIv, callback);
			}
		});
	}

	private void acceptConnectionSync(TransportId t, byte[] encryptedIv,
			Callback callback) {
		try {
			if(encryptedIv.length != IV_LENGTH)
				throw new IllegalArgumentException();
			if(!initialised.getAndSet(true)) initialise();
			Context ctx;
			synchronized(this) {
				Bytes b = new Bytes(encryptedIv);
				ctx = expected.get(b);
				if(ctx == null || !ctx.transportId.equals(t)) {
					callback.connectionRejected();
					return;
				}
				expected.remove(b);
			}
			// The IV was expected
			ContactId c = ctx.contactId;
			TransportIndex i = ctx.transportIndex;
			long connection = ctx.connection;
			ConnectionWindow w = ctx.window;
			byte[] secret;
			synchronized(w) {
				// Get the secret and update the connection window
				secret = w.setSeen(connection);
				try {
					db.setConnectionWindow(c, i, w);
				} catch(NoSuchContactException e) {
					// The contact was removed - clean up in eventOccurred()
				}
			}
			// Update the set of expected IVs
			Map<Bytes, Context> ivs = calculateIvs(c, t, i, w);
			synchronized(this) {
				Iterator<Context> it = expected.values().iterator();
				while(it.hasNext()) {
					Context ctx1 = it.next();
					if(ctx1.contactId.equals(c)
							&& ctx1.transportIndex.equals(i)) it.remove();
				}
				expected.putAll(ivs);
			}
			callback.connectionAccepted(new ConnectionContextImpl(c, i,
					connection, secret));
		} catch(DbException e) {
			callback.handleException(e);
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			// Remove the expected IVs for the ex-contact
			removeIvs(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof TransportAddedEvent) {
			// Calculate the expected IVs for the new transport
			TransportId t = ((TransportAddedEvent) e).getTransportId();
			try {
				if(!initialised.getAndSet(true)) initialise();
				Map<Bytes, Context> ivs = calculateIvs(t);
				synchronized(this) {
					localTransportIds.add(t);
					expected.putAll(ivs);
				}
			} catch(DbException e1) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning(e1.getMessage());
			}
		} else if(e instanceof RemoteTransportsUpdatedEvent) {
			// Remove and recalculate the expected IVs for the contact
			ContactId c = ((RemoteTransportsUpdatedEvent) e).getContactId();
			try {
				if(!initialised.getAndSet(true)) initialise();
				Map<Bytes, Context> ivs = calculateIvs(c);
				synchronized(this) {
					removeIvs(c);
					expected.putAll(ivs);
				}
			} catch(DbException e1) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning(e1.getMessage());
			}
		}
	}

	private synchronized void removeIvs(ContactId c) {
		Iterator<Context> it = expected.values().iterator();
		while(it.hasNext()) if(it.next().contactId.equals(c)) it.remove();
	}

	private Map<Bytes, Context> calculateIvs(TransportId t) throws DbException {
		Map<Bytes, Context> ivs = new HashMap<Bytes, Context>();
		for(ContactId c : db.getContacts()) {
			try {
				TransportIndex i = db.getRemoteIndex(c, t);
				if(i != null) {
					ConnectionWindow w = db.getConnectionWindow(c, i);
					ivs.putAll(calculateIvs(c, t, i, w));
				}
			} catch(NoSuchContactException e) {
				// The contact was removed - clean up in eventOccurred()
			}
		}
		return ivs;
	}

	private static class Context {

		private final ContactId contactId;
		private final TransportId transportId;
		private final TransportIndex transportIndex;
		private final long connection;
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
