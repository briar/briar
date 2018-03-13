package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.api.transport.KeySet;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.transport.ReorderingWindow.Change;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.api.transport.TransportConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;

@ThreadSafe
@NotNullByDefault
class TransportKeyManagerImpl implements TransportKeyManager {

	private static final Logger LOG =
			Logger.getLogger(TransportKeyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final TransportCrypto transportCrypto;
	private final Executor dbExecutor;
	private final ScheduledExecutorService scheduler;
	private final Clock clock;
	private final TransportId transportId;
	private final long rotationPeriodLength;
	private final AtomicBoolean used = new AtomicBoolean(false);
	private final ReentrantLock lock = new ReentrantLock();

	// The following are locking: lock
	private final Collection<MutableKeySet> keys = new ArrayList<>();
	private final Map<Bytes, TagContext> inContexts = new HashMap<>();
	private final Map<ContactId, MutableKeySet> outContexts = new HashMap<>();

	TransportKeyManagerImpl(DatabaseComponent db,
			TransportCrypto transportCrypto, Executor dbExecutor,
			@Scheduler ScheduledExecutorService scheduler, Clock clock,
			TransportId transportId, long maxLatency) {
		this.db = db;
		this.transportCrypto = transportCrypto;
		this.dbExecutor = dbExecutor;
		this.scheduler = scheduler;
		this.clock = clock;
		this.transportId = transportId;
		rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
	}

	@Override
	public void start(Transaction txn) throws DbException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		long now = clock.currentTimeMillis();
		lock.lock();
		try {
			// Load the transport keys from the DB
			Collection<KeySet> loaded = db.getTransportKeys(txn, transportId);
			// Rotate the keys to the current rotation period
			RotationResult rotationResult = rotateKeys(loaded, now);
			// Initialise mutable state for all contacts
			addKeys(rotationResult.current);
			// Write any rotated keys back to the DB
			if (!rotationResult.rotated.isEmpty())
				db.updateTransportKeys(txn, rotationResult.rotated);
		} finally {
			lock.unlock();
		}
		// Schedule the next key rotation
		scheduleKeyRotation(now);
	}

	private RotationResult rotateKeys(Collection<KeySet> keys, long now) {
		RotationResult rotationResult = new RotationResult();
		long rotationPeriod = now / rotationPeriodLength;
		for (KeySet ks : keys) {
			TransportKeys k = ks.getTransportKeys();
			TransportKeys k1 =
					transportCrypto.rotateTransportKeys(k, rotationPeriod);
			KeySet ks1 = new KeySet(ks.getKeySetId(), ks.getContactId(), k1);
			if (k1.getRotationPeriod() > k.getRotationPeriod())
				rotationResult.rotated.add(ks1);
			rotationResult.current.add(ks1);
		}
		return rotationResult;
	}

	// Locking: lock
	private void addKeys(Collection<KeySet> keys) {
		for (KeySet ks : keys) {
			addKeys(ks.getKeySetId(), ks.getContactId(),
					new MutableTransportKeys(ks.getTransportKeys()));
		}
	}

	// Locking: lock
	private void addKeys(KeySetId keySetId, @Nullable ContactId contactId,
			MutableTransportKeys m) {
		MutableKeySet ks = new MutableKeySet(keySetId, contactId, m);
		keys.add(ks);
		if (contactId != null) {
			encodeTags(keySetId, contactId, m.getPreviousIncomingKeys());
			encodeTags(keySetId, contactId, m.getCurrentIncomingKeys());
			encodeTags(keySetId, contactId, m.getNextIncomingKeys());
			// Use the outgoing keys with the highest key set ID
			MutableKeySet old = outContexts.get(contactId);
			if (old == null || old.getKeySetId().getInt() < keySetId.getInt())
				outContexts.put(contactId, ks);
		}
	}

	// Locking: lock
	private void encodeTags(KeySetId keySetId, ContactId contactId,
			MutableIncomingKeys inKeys) {
		for (long streamNumber : inKeys.getWindow().getUnseen()) {
			TagContext tagCtx =
					new TagContext(keySetId, contactId, inKeys, streamNumber);
			byte[] tag = new byte[TAG_LENGTH];
			transportCrypto.encodeTag(tag, inKeys.getTagKey(), PROTOCOL_VERSION,
					streamNumber);
			inContexts.put(new Bytes(tag), tagCtx);
		}
	}

	private void scheduleKeyRotation(long now) {
		long delay = rotationPeriodLength - now % rotationPeriodLength;
		scheduler.schedule((Runnable) this::rotateKeys, delay, MILLISECONDS);
	}

	private void rotateKeys() {
		dbExecutor.execute(() -> {
			try {
				Transaction txn = db.startTransaction(false);
				try {
					rotateKeys(txn);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		});
	}

	@Override
	public void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		lock.lock();
		try {
			// Work out what rotation period the timestamp belongs to
			long rotationPeriod = timestamp / rotationPeriodLength;
			// Derive the transport keys
			TransportKeys k = transportCrypto.deriveTransportKeys(transportId,
					master, rotationPeriod, alice);
			// Rotate the keys to the current rotation period if necessary
			rotationPeriod = clock.currentTimeMillis() / rotationPeriodLength;
			k = transportCrypto.rotateTransportKeys(k, rotationPeriod);
			// Write the keys back to the DB
			KeySetId keySetId = db.addTransportKeys(txn, c, k);
			// Initialise mutable state for the contact
			addKeys(keySetId, c, new MutableTransportKeys(k));
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void removeContact(ContactId c) {
		lock.lock();
		try {
			// Remove mutable state for the contact
			Iterator<Entry<Bytes, TagContext>> inContextsIter =
					inContexts.entrySet().iterator();
			while (inContextsIter.hasNext()) {
				ContactId c1 = inContextsIter.next().getValue().contactId;
				if (c1.equals(c)) inContextsIter.remove();
			}
			outContexts.remove(c);
			Iterator<MutableKeySet> keysIter = keys.iterator();
			while (keysIter.hasNext()) {
				ContactId c1 = keysIter.next().getContactId();
				if (c1 != null && c1.equals(c)) keysIter.remove();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public StreamContext getStreamContext(Transaction txn, ContactId c)
			throws DbException {
		lock.lock();
		try {
			// Look up the outgoing keys for the contact
			MutableKeySet ks = outContexts.get(c);
			if (ks == null) return null;
			MutableOutgoingKeys outKeys =
					ks.getTransportKeys().getCurrentOutgoingKeys();
			if (outKeys.getStreamCounter() > MAX_32_BIT_UNSIGNED) return null;
			// Create a stream context
			StreamContext ctx = new StreamContext(c, transportId,
					outKeys.getTagKey(), outKeys.getHeaderKey(),
					outKeys.getStreamCounter());
			// Increment the stream counter and write it back to the DB
			outKeys.incrementStreamCounter();
			db.incrementStreamCounter(txn, c, transportId,
					outKeys.getRotationPeriod());
			return ctx;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public StreamContext getStreamContext(Transaction txn, byte[] tag)
			throws DbException {
		lock.lock();
		try {
			// Look up the incoming keys for the tag
			TagContext tagCtx = inContexts.remove(new Bytes(tag));
			if (tagCtx == null) return null;
			MutableIncomingKeys inKeys = tagCtx.inKeys;
			// Create a stream context
			StreamContext ctx = new StreamContext(tagCtx.contactId, transportId,
					inKeys.getTagKey(), inKeys.getHeaderKey(),
					tagCtx.streamNumber);
			// Update the reordering window
			ReorderingWindow window = inKeys.getWindow();
			Change change = window.setSeen(tagCtx.streamNumber);
			// Add tags for any stream numbers added to the window
			for (long streamNumber : change.getAdded()) {
				byte[] addTag = new byte[TAG_LENGTH];
				transportCrypto.encodeTag(addTag, inKeys.getTagKey(),
						PROTOCOL_VERSION, streamNumber);
				TagContext tagCtx1 = new TagContext(tagCtx.keySetId,
						tagCtx.contactId, inKeys, streamNumber);
				inContexts.put(new Bytes(addTag), tagCtx1);
			}
			// Remove tags for any stream numbers removed from the window
			for (long streamNumber : change.getRemoved()) {
				if (streamNumber == tagCtx.streamNumber) continue;
				byte[] removeTag = new byte[TAG_LENGTH];
				transportCrypto.encodeTag(removeTag, inKeys.getTagKey(),
						PROTOCOL_VERSION, streamNumber);
				inContexts.remove(new Bytes(removeTag));
			}
			// Write the window back to the DB
			db.setReorderingWindow(txn, tagCtx.keySetId, transportId,
					inKeys.getRotationPeriod(), window.getBase(),
					window.getBitmap());
			return ctx;
		} finally {
			lock.unlock();
		}
	}

	private void rotateKeys(Transaction txn) throws DbException {
		long now = clock.currentTimeMillis();
		lock.lock();
		try {
			// Rotate the keys to the current rotation period
			Collection<KeySet> snapshot = new ArrayList<>(keys.size());
			for (MutableKeySet ks : keys) {
				snapshot.add(new KeySet(ks.getKeySetId(), ks.getContactId(),
						ks.getTransportKeys().snapshot()));
			}
			RotationResult rotationResult = rotateKeys(snapshot, now);
			// Rebuild the mutable state for all contacts
			inContexts.clear();
			outContexts.clear();
			keys.clear();
			addKeys(rotationResult.current);
			// Write any rotated keys back to the DB
			if (!rotationResult.rotated.isEmpty())
				db.updateTransportKeys(txn, rotationResult.rotated);
		} finally {
			lock.unlock();
		}
		// Schedule the next key rotation
		scheduleKeyRotation(now);
	}

	private static class TagContext {

		private final KeySetId keySetId;
		private final ContactId contactId;
		private final MutableIncomingKeys inKeys;
		private final long streamNumber;

		private TagContext(KeySetId keySetId, ContactId contactId,
				MutableIncomingKeys inKeys, long streamNumber) {
			this.keySetId = keySetId;
			this.contactId = contactId;
			this.inKeys = inKeys;
			this.streamNumber = streamNumber;
		}
	}

	private static class RotationResult {

		private final Collection<KeySet> current = new ArrayList<>();
		private final Collection<KeySet> rotated = new ArrayList<>();
	}
}
