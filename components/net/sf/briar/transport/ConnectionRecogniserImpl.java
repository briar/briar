package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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

import com.google.inject.Inject;

class ConnectionRecogniserImpl implements ConnectionRecogniser,
DatabaseListener {

	private static final Logger LOG =
		Logger.getLogger(ConnectionRecogniserImpl.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final Cipher ivCipher;
	private final Map<Bytes, ConnectionContext> expected;
	private final Collection<TransportId> localTransportIds;

	private boolean initialised = false;

	@Inject
	ConnectionRecogniserImpl(CryptoComponent crypto, DatabaseComponent db) {
		this.crypto = crypto;
		this.db = db;
		ivCipher = crypto.getIvCipher();
		expected = new HashMap<Bytes, ConnectionContext>();
		localTransportIds = new ArrayList<TransportId>();
		db.addListener(this);
	}

	private synchronized void initialise() throws DbException {
		for(Transport t : db.getLocalTransports()) {
			localTransportIds.add(t.getId());
		}
		for(ContactId c : db.getContacts()) {
			try {
				calculateIvs(c);
			} catch(NoSuchContactException e) {
				// The contact was removed - clean up in eventOccurred()
			}
		}
		initialised = true;
	}

	private synchronized void calculateIvs(ContactId c) throws DbException {
		for(TransportId t : localTransportIds) {
			TransportIndex i = db.getRemoteIndex(c, t);
			if(i != null) {
				ConnectionWindow w = db.getConnectionWindow(c, i);
				calculateIvs(c, i, w);
			}
		}
	}

	private synchronized void calculateIvs(ContactId c, TransportIndex i,
			ConnectionWindow w) throws DbException {
		for(Entry<Long, byte[]> e : w.getUnseen().entrySet()) {
			long unseen = e.getKey();
			byte[] secret = e.getValue();
			ErasableKey ivKey = crypto.deriveIvKey(secret, true);
			Bytes iv = new Bytes(encryptIv(i, unseen, ivKey));
			ivKey.erase();
			expected.put(iv, new ConnectionContextImpl(c, i, unseen, secret));
		}
	}

	private synchronized byte[] encryptIv(TransportIndex i, long connection,
			ErasableKey ivKey) {
		byte[] iv = IvEncoder.encodeIv(true, i.getInt(), connection);
		try {
			ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
			return ivCipher.doFinal(iv);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
	}

	public synchronized ConnectionContext acceptConnection(byte[] encryptedIv)
	throws DbException {
		if(encryptedIv.length != IV_LENGTH)
			throw new IllegalArgumentException();
		if(!initialised) initialise();
		ConnectionContext ctx = expected.remove(new Bytes(encryptedIv));
		if(ctx == null) return null; // The IV was not expected
		try {
			ContactId c = ctx.getContactId();
			TransportIndex i = ctx.getTransportIndex();
			// Update the connection window
			ConnectionWindow w = db.getConnectionWindow(c, i);
			w.setSeen(ctx.getConnectionNumber());
			db.setConnectionWindow(c, i, w);
			// Update the set of expected IVs
			Iterator<ConnectionContext> it = expected.values().iterator();
			while(it.hasNext()) {
				ConnectionContext ctx1 = it.next();
				ContactId c1 = ctx1.getContactId();
				TransportIndex i1 = ctx1.getTransportIndex();
				if(c1.equals(c) && i1.equals(i)) it.remove();
			}
			calculateIvs(c, i, w);
		} catch(NoSuchContactException e) {
			// The contact was removed - clean up when we get the event
		}
		return ctx;
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			// Remove the expected IVs for the ex-contact
			removeIvs(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof TransportAddedEvent) {
			// Calculate the expected IVs for the new transport
			TransportId t = ((TransportAddedEvent) e).getTransportId();
			synchronized(this) {
				if(!initialised) return;
				try {
					localTransportIds.add(t);
					calculateIvs(t);
				} catch(DbException e1) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e1.getMessage());
				}
			}
		} else if(e instanceof RemoteTransportsUpdatedEvent) {
			// Remove and recalculate the expected IVs for the contact
			ContactId c = ((RemoteTransportsUpdatedEvent) e).getContactId();
			synchronized(this) {
				if(!initialised) return;
				removeIvs(c);
				try {
					calculateIvs(c);
				} catch(DbException e1) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e1.getMessage());
				}
			}
		}
	}

	private synchronized void removeIvs(ContactId c) {
		if(!initialised) return;
		Iterator<ConnectionContext> it = expected.values().iterator();
		while(it.hasNext()) if(it.next().getContactId().equals(c)) it.remove();
	}

	private synchronized void calculateIvs(TransportId t) throws DbException {
		for(ContactId c : db.getContacts()) {
			try {
				TransportIndex i = db.getRemoteIndex(c, t);
				if(i != null) {
					ConnectionWindow w = db.getConnectionWindow(c, i);
					calculateIvs(c, i, w);
				}
			} catch(NoSuchContactException e) {
				// The contact was removed - clean up when we get the event
			}
		}
	}
}
