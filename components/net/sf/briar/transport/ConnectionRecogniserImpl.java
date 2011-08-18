package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseListener;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionWindow;

class ConnectionRecogniserImpl implements ConnectionRecogniser,
DatabaseListener {

	private final int transportId;
	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final Map<Bytes, ContactId> tagToContact;
	private final Map<Bytes, Long> tagToConnectionNumber;
	private final Map<ContactId, Map<Long, Bytes>> contactToTags;
	private final Map<ContactId, Cipher> contactToCipher;
	private final Map<ContactId, ConnectionWindow> contactToWindow;
	private boolean initialised = false;

	ConnectionRecogniserImpl(int transportId, CryptoComponent crypto,
			DatabaseComponent db) {
		this.transportId = transportId;
		this.crypto = crypto;
		this.db = db;
		// FIXME: There's probably a tidier way of maintaining all this state
		tagToContact = new HashMap<Bytes, ContactId>();
		tagToConnectionNumber = new HashMap<Bytes, Long>();
		contactToTags = new HashMap<ContactId, Map<Long, Bytes>>();
		contactToCipher = new HashMap<ContactId, Cipher>();
		contactToWindow = new HashMap<ContactId, ConnectionWindow>();
		db.addListener(this);
	}

	private synchronized void initialise() throws DbException {
		for(ContactId c : db.getContacts()) {
			try {
				// Initialise and store the contact's tag cipher
				byte[] secret = db.getSharedSecret(c);
				SecretKey tagKey = crypto.deriveIncomingTagKey(secret);
				Cipher cipher = crypto.getTagCipher();
				try {
					cipher.init(Cipher.ENCRYPT_MODE, tagKey);
				} catch(InvalidKeyException badKey) {
					throw new RuntimeException(badKey);
				}
				contactToCipher.put(c, cipher);
				// Calculate the tags for the contact's connection window
				ConnectionWindow w = db.getConnectionWindow(c, transportId);
				Map<Long, Bytes> tags = new HashMap<Long, Bytes>();
				for(Long unseen : w.getUnseenConnectionNumbers()) {
					Bytes expectedTag = new Bytes(calculateTag(c, unseen));
					tagToContact.put(expectedTag, c);
					tagToConnectionNumber.put(expectedTag, unseen);
					tags.put(unseen, expectedTag);
				}
				contactToTags.put(c, tags);
				contactToWindow.put(c, w);
			} catch(NoSuchContactException e) {
				// The contact was removed after the call to getContacts()
				continue;
			}
		}
		initialised = true;
	}

	private synchronized byte[] calculateTag(ContactId c, long connection) {
		byte[] tag = TagEncoder.encodeTag(transportId, connection);
		Cipher cipher = contactToCipher.get(c);
		assert cipher != null;
		try {
			return cipher.doFinal(tag);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
	}

	public synchronized ContactId acceptConnection(byte[] tag)
	throws DbException {
		if(tag.length != TAG_LENGTH)
			throw new IllegalArgumentException();
		if(!initialised) initialise();
		Bytes b = new Bytes(tag);
		ContactId contactId = tagToContact.remove(b);
		Long connection = tagToConnectionNumber.remove(b);
		assert (contactId == null) == (connection == null);
		if(contactId == null) return null;
		// The tag was expected - update and save the connection window
		ConnectionWindow w = contactToWindow.get(contactId);
		assert w != null;
		w.setSeen(connection);
		db.setConnectionWindow(contactId, transportId, w);
		// Update the set of expected tags
		Map<Long, Bytes> oldTags = contactToTags.remove(contactId);
		assert oldTags != null;
		assert oldTags.containsKey(connection);
		Map<Long, Bytes> newTags = new HashMap<Long, Bytes>();
		for(Long unseen : w.getUnseenConnectionNumbers()) {
			Bytes expectedTag = oldTags.get(unseen);
			if(expectedTag == null) {
				expectedTag = new Bytes(calculateTag(contactId, unseen));
				tagToContact.put(expectedTag, contactId);
				tagToConnectionNumber.put(expectedTag, connection);
			}
			newTags.put(unseen, expectedTag);
		}
		contactToTags.put(contactId, newTags);
		return contactId;
	}

	public void eventOccurred(Event e) {
		// When the set of contacts changes we need to re-initialise everything
		if(e == Event.CONTACTS_UPDATED) {
			synchronized(this) {
				initialised = false;
			}
		}
	}
}
