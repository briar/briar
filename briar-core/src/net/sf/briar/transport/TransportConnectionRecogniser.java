package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.SecretKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.TemporarySecret;

// FIXME: Don't make alien calls with a lock held
/** A connection recogniser for a specific transport. */
class TransportConnectionRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final TransportId transportId;
	private final Map<Bytes, TagContext> tagMap; // Locking: this
	private final Map<RemovalKey, RemovalContext> removalMap; // Locking: this

	TransportConnectionRecogniser(CryptoComponent crypto, DatabaseComponent db,
			TransportId transportId) {
		this.crypto = crypto;
		this.db = db;
		this.transportId = transportId;
		tagMap = new HashMap<Bytes, TagContext>();
		removalMap = new HashMap<RemovalKey, RemovalContext>();
	}

	synchronized ConnectionContext acceptConnection(byte[] tag)
			throws DbException {
		TagContext t = tagMap.remove(new Bytes(tag));
		if(t == null) return null; // The tag was not expected
		// Update the connection window and the expected tags
		SecretKey key = crypto.deriveTagKey(t.secret, !t.alice);
		for(long connection : t.window.setSeen(t.connection)) {
			byte[] tag1 = new byte[TAG_LENGTH];
			crypto.encodeTag(tag1, key, connection);
			if(connection < t.connection) {
				TagContext removed = tagMap.remove(new Bytes(tag1));
				assert removed != null;
			} else {
				TagContext added = new TagContext(t, connection);
				TagContext duplicate = tagMap.put(new Bytes(tag1), added);
				assert duplicate == null;
			}
		}
		key.erase();
		// Store the updated connection window in the DB
		db.setConnectionWindow(t.contactId, transportId, t.period,
				t.window.getCentre(), t.window.getBitmap());
		// Clone the secret - the key manager will erase the original
		byte[] secret = t.secret.clone();
		return new ConnectionContext(t.contactId, transportId, secret,
				t.connection, t.alice);
	}

	synchronized void addSecret(TemporarySecret s) {
		ContactId contactId = s.getContactId();
		boolean alice = s.getAlice();
		long period = s.getPeriod();
		byte[] secret = s.getSecret();
		long centre = s.getWindowCentre();
		byte[] bitmap = s.getWindowBitmap();
		// Create the connection window and the expected tags
		SecretKey key = crypto.deriveTagKey(secret, !alice);
		ConnectionWindow window = new ConnectionWindow(centre, bitmap);
		for(long connection : window.getUnseen()) {
			byte[] tag = new byte[TAG_LENGTH];
			crypto.encodeTag(tag, key, connection);
			TagContext added = new TagContext(contactId, alice, period,
					secret, window, connection);
			TagContext duplicate = tagMap.put(new Bytes(tag), added);
			assert duplicate == null;
		}
		key.erase();
		// Create a removal context to remove the window and the tags later
		RemovalContext r = new RemovalContext(window, secret, alice);
		removalMap.put(new RemovalKey(contactId, period), r);
	}

	synchronized void removeSecret(ContactId contactId, long period) {
		RemovalKey k = new RemovalKey(contactId, period);
		RemovalContext removed = removalMap.remove(k);
		if(removed == null) throw new IllegalArgumentException();
		removeSecret(removed);
	}

	// Locking: this
	private void removeSecret(RemovalContext r) {
		// Remove the expected tags
		SecretKey key = crypto.deriveTagKey(r.secret, !r.alice);
		byte[] tag = new byte[TAG_LENGTH];
		for(long connection : r.window.getUnseen()) {
			crypto.encodeTag(tag, key, connection);
			TagContext removed = tagMap.remove(new Bytes(tag));
			assert removed != null;
		}
		key.erase();
	}

	synchronized void removeSecrets(ContactId c) {
		Collection<RemovalKey> keysToRemove = new ArrayList<RemovalKey>();
		for(RemovalKey k : removalMap.keySet())
			if(k.contactId.equals(c)) keysToRemove.add(k);
		for(RemovalKey k : keysToRemove) removeSecret(k.contactId, k.period);
	}

	synchronized void removeSecrets() {
		for(RemovalContext r : removalMap.values()) removeSecret(r);
		assert tagMap.isEmpty();
		removalMap.clear();
	}

	private static class TagContext {

		private final ContactId contactId;
		private final boolean alice;
		private final long period;
		private final byte[] secret;
		private final ConnectionWindow window;
		private final long connection;

		private TagContext(ContactId contactId, boolean alice, long period,
				byte[] secret, ConnectionWindow window, long connection) {
			this.contactId = contactId;
			this.alice = alice;
			this.period = period;
			this.secret = secret;
			this.window = window;
			this.connection = connection;
		}

		private TagContext(TagContext t, long connection) {
			this(t.contactId, t.alice, t.period, t.secret, t.window,
					connection);
		}
	}

	private static class RemovalKey {

		private final ContactId contactId;
		private final long period;

		private RemovalKey(ContactId contactId, long period) {
			this.contactId = contactId;
			this.period = period;
		}

		@Override
		public int hashCode() {
			return contactId.hashCode() ^ (int) (period ^ (period >>> 32));
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof RemovalKey) {
				RemovalKey k = (RemovalKey) o;
				return contactId.equals(k.contactId) && period == k.period;
			}
			return false;
		}
	}

	private static class RemovalContext {

		private final ConnectionWindow window;
		private final byte[] secret;
		private final boolean alice;

		private RemovalContext(ConnectionWindow window, byte[] secret,
				boolean alice) {
			this.window = window;
			this.secret = secret;
			this.alice = alice;
		}
	}
}
