package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.briarproject.api.Bytes;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TemporarySecret;

// FIXME: Don't make alien calls with a lock held
/**
 * A {@link org.briarproject.api.transport.TagRecogniser TagRecogniser} for a
 * specific transport.
 */
class TransportTagRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final TransportId transportId;
	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private final Map<Bytes, TagContext> tagMap;
	private final Map<RemovalKey, RemovalContext> removalMap;

	TransportTagRecogniser(CryptoComponent crypto, DatabaseComponent db,
			TransportId transportId) {
		this.crypto = crypto;
		this.db = db;
		this.transportId = transportId;
		tagMap = new HashMap<Bytes, TagContext>();
		removalMap = new HashMap<RemovalKey, RemovalContext>();
	}

	StreamContext recogniseTag(byte[] tag) throws DbException {
		lock.lock();
		try {
			TagContext t = tagMap.remove(new Bytes(tag));
			if (t == null) return null; // The tag was not expected
			// Update the reordering window and the expected tags
			SecretKey key = crypto.deriveTagKey(t.secret, !t.alice);
			for (long streamNumber : t.window.setSeen(t.streamNumber)) {
				byte[] tag1 = new byte[TAG_LENGTH];
				crypto.encodeTag(tag1, key, streamNumber);
				if (streamNumber < t.streamNumber) {
					TagContext removed = tagMap.remove(new Bytes(tag1));
					assert removed != null;
				} else {
					TagContext added = new TagContext(t, streamNumber);
					TagContext duplicate = tagMap.put(new Bytes(tag1), added);
					assert duplicate == null;
				}
			}
			// Store the updated reordering window in the DB
			db.setReorderingWindow(t.contactId, transportId, t.period,
					t.window.getCentre(), t.window.getBitmap());
			return new StreamContext(t.contactId, transportId, t.secret,
					t.streamNumber, t.alice);
		} finally {
			lock.unlock();
		}
	}

	void addSecret(TemporarySecret s) {
		lock.lock();
		try {
			ContactId contactId = s.getContactId();
			boolean alice = s.getAlice();
			long period = s.getPeriod();
			byte[] secret = s.getSecret();
			long centre = s.getWindowCentre();
			byte[] bitmap = s.getWindowBitmap();
			// Create the reordering window and the expected tags
			SecretKey key = crypto.deriveTagKey(secret, !alice);
			ReorderingWindow window = new ReorderingWindow(centre, bitmap);
			for (long streamNumber : window.getUnseen()) {
				byte[] tag = new byte[TAG_LENGTH];
				crypto.encodeTag(tag, key, streamNumber);
				TagContext added = new TagContext(contactId, alice, period,
						secret, window, streamNumber);
				TagContext duplicate = tagMap.put(new Bytes(tag), added);
				assert duplicate == null;
			}
			// Create a removal context to remove the window and the tags later
			RemovalContext r = new RemovalContext(window, secret, alice);
			removalMap.put(new RemovalKey(contactId, period), r);
		} finally {
			lock.unlock();
		}
	}

	void removeSecret(ContactId contactId, long period) {
		lock.lock();
		try {
			RemovalKey k = new RemovalKey(contactId, period);
			RemovalContext removed = removalMap.remove(k);
			if (removed == null) throw new IllegalArgumentException();
			removeSecret(removed);
		} finally {
			lock.unlock();
		}
	}

	// Locking: lock
	private void removeSecret(RemovalContext r) {
		// Remove the expected tags
		SecretKey key = crypto.deriveTagKey(r.secret, !r.alice);
		byte[] tag = new byte[TAG_LENGTH];
		for (long streamNumber : r.window.getUnseen()) {
			crypto.encodeTag(tag, key, streamNumber);
			TagContext removed = tagMap.remove(new Bytes(tag));
			assert removed != null;
		}
	}

	void removeSecrets(ContactId c) {
		lock.lock();
		try {
			Collection<RemovalKey> keysToRemove = new ArrayList<RemovalKey>();
			for (RemovalKey k : removalMap.keySet())
				if (k.contactId.equals(c)) keysToRemove.add(k);
			for (RemovalKey k : keysToRemove)
				removeSecret(k.contactId, k.period);
		} finally {
			lock.unlock();
		}
	}

	void removeSecrets() {
		lock.lock();
		try {
			for (RemovalContext r : removalMap.values()) removeSecret(r);
			assert tagMap.isEmpty();
			removalMap.clear();
		} finally {
			lock.unlock();
		}
	}

	private static class TagContext {

		private final ContactId contactId;
		private final boolean alice;
		private final long period;
		private final byte[] secret;
		private final ReorderingWindow window;
		private final long streamNumber;

		private TagContext(ContactId contactId, boolean alice, long period,
				byte[] secret, ReorderingWindow window, long streamNumber) {
			this.contactId = contactId;
			this.alice = alice;
			this.period = period;
			this.secret = secret;
			this.window = window;
			this.streamNumber = streamNumber;
		}

		private TagContext(TagContext t, long streamNumber) {
			this(t.contactId, t.alice, t.period, t.secret, t.window,
					streamNumber);
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
			if (o instanceof RemovalKey) {
				RemovalKey k = (RemovalKey) o;
				return contactId.equals(k.contactId) && period == k.period;
			}
			return false;
		}
	}

	private static class RemovalContext {

		private final ReorderingWindow window;
		private final byte[] secret;
		private final boolean alice;

		private RemovalContext(ReorderingWindow window, byte[] secret,
				boolean alice) {
			this.window = window;
			this.secret = secret;
			this.alice = alice;
		}
	}
}
