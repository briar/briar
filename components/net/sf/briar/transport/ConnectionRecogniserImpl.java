package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionWindow;

class ConnectionRecogniserImpl implements ConnectionRecogniser,
DatabaseListener {

	private final TransportId id;
	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final Map<Bytes, ContactId> ivToContact;
	private final Map<Bytes, Long> ivToConnectionNumber;
	private final Map<ContactId, Map<Long, Bytes>> contactToIvs;
	private final Map<ContactId, Cipher> contactToCipher;
	private final Map<ContactId, ConnectionWindow> contactToWindow;
	private boolean initialised = false;

	ConnectionRecogniserImpl(TransportId id, CryptoComponent crypto,
			DatabaseComponent db) {
		this.id = id;
		this.crypto = crypto;
		this.db = db;
		// FIXME: There's probably a tidier way of maintaining all this state
		ivToContact = new HashMap<Bytes, ContactId>();
		ivToConnectionNumber = new HashMap<Bytes, Long>();
		contactToIvs = new HashMap<ContactId, Map<Long, Bytes>>();
		contactToCipher = new HashMap<ContactId, Cipher>();
		contactToWindow = new HashMap<ContactId, ConnectionWindow>();
		db.addListener(this);
	}

	private synchronized void initialise() throws DbException {
		for(ContactId c : db.getContacts()) {
			try {
				// Initialise and store the contact's IV cipher
				byte[] secret = db.getSharedSecret(c);
				SecretKey ivKey = crypto.deriveIncomingIvKey(secret);
				Cipher cipher = crypto.getIvCipher();
				try {
					cipher.init(Cipher.ENCRYPT_MODE, ivKey);
				} catch(InvalidKeyException badKey) {
					throw new RuntimeException(badKey);
				}
				contactToCipher.put(c, cipher);
				// Calculate the IVs for the contact's connection window
				ConnectionWindow w = db.getConnectionWindow(c, id);
				Map<Long, Bytes> ivs = new HashMap<Long, Bytes>();
				for(Long unseen : w.getUnseenConnectionNumbers()) {
					Bytes expectedIv = new Bytes(encryptIv(c, unseen));
					ivToContact.put(expectedIv, c);
					ivToConnectionNumber.put(expectedIv, unseen);
					ivs.put(unseen, expectedIv);
				}
				contactToIvs.put(c, ivs);
				contactToWindow.put(c, w);
			} catch(NoSuchContactException e) {
				// The contact was removed after the call to getContacts()
				continue;
			}
		}
		initialised = true;
	}

	private synchronized byte[] encryptIv(ContactId c, long connection) {
		byte[] iv = IvEncoder.encodeIv(true, id, connection);
		Cipher cipher = contactToCipher.get(c);
		assert cipher != null;
		try {
			return cipher.doFinal(iv);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
	}

	public synchronized ContactId acceptConnection(byte[] encryptedIv)
	throws DbException {
		if(encryptedIv.length != IV_LENGTH)
			throw new IllegalArgumentException();
		if(!initialised) initialise();
		Bytes b = new Bytes(encryptedIv);
		ContactId contactId = ivToContact.remove(b);
		Long connection = ivToConnectionNumber.remove(b);
		assert (contactId == null) == (connection == null);
		if(contactId == null) return null;
		// The IV was expected - update and save the connection window
		ConnectionWindow w = contactToWindow.get(contactId);
		assert w != null;
		w.setSeen(connection);
		db.setConnectionWindow(contactId, id, w);
		// Update the set of expected IVs
		Map<Long, Bytes> oldIvs = contactToIvs.remove(contactId);
		assert oldIvs != null;
		assert oldIvs.containsKey(connection);
		Map<Long, Bytes> newIvs = new HashMap<Long, Bytes>();
		for(Long unseen : w.getUnseenConnectionNumbers()) {
			Bytes expectedIv = oldIvs.get(unseen);
			if(expectedIv == null) {
				expectedIv = new Bytes(encryptIv(contactId, unseen));
				ivToContact.put(expectedIv, contactId);
				ivToConnectionNumber.put(expectedIv, connection);
			}
			newIvs.put(unseen, expectedIv);
		}
		contactToIvs.put(contactId, newIvs);
		return contactId;
	}

	public void eventOccurred(DatabaseEvent e) {
		// When the set of contacts changes we need to re-initialise everything
		if(e instanceof ContactAddedEvent || e instanceof ContactRemovedEvent) {
			synchronized(this) {
				initialised = false;
			}
		}
	}
}
