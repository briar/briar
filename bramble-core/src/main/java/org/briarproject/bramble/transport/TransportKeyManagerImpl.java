package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.transport.ReorderingWindow.Change;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;

@ThreadSafe
@NotNullByDefault
class TransportKeyManagerImpl implements TransportKeyManager {

	private static final Logger LOG =
			Logger.getLogger(TransportKeyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final Executor dbExecutor;
	private final ScheduledExecutorService scheduler;
	private final Clock clock;
	private final TransportId transportId;
	private final long rotationPeriodLength;
	private final ReentrantLock lock;

	// The following are locking: lock
	private final Map<Bytes, TagContext> inContexts;
	private final Map<ContactId, MutableOutgoingKeys> outContexts;
	private final Map<ContactId, MutableTransportKeys> keys;

	TransportKeyManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			Executor dbExecutor, @Scheduler ScheduledExecutorService scheduler,
			Clock clock, TransportId transportId, long maxLatency) {
		this.db = db;
		this.crypto = crypto;
		this.dbExecutor = dbExecutor;
		this.scheduler = scheduler;
		this.clock = clock;
		this.transportId = transportId;
		rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
		lock = new ReentrantLock();
		inContexts = new HashMap<Bytes, TagContext>();
		outContexts = new HashMap<ContactId, MutableOutgoingKeys>();
		keys = new HashMap<ContactId, MutableTransportKeys>();
	}

	@Override
	public void start(Transaction txn) throws DbException {
		long now = clock.currentTimeMillis();
		lock.lock();
		try {
			// Load the transport keys from the DB
			Map<ContactId, TransportKeys> loaded =
					db.getTransportKeys(txn, transportId);
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

	private RotationResult rotateKeys(Map<ContactId, TransportKeys> keys,
			long now) {
		RotationResult rotationResult = new RotationResult();
		long rotationPeriod = now / rotationPeriodLength;
		for (Entry<ContactId, TransportKeys> e : keys.entrySet()) {
			ContactId c = e.getKey();
			TransportKeys k = e.getValue();
			TransportKeys k1 = crypto.rotateTransportKeys(k, rotationPeriod);
			if (k1.getRotationPeriod() > k.getRotationPeriod())
				rotationResult.rotated.put(c, k1);
			rotationResult.current.put(c, k1);
		}
		return rotationResult;
	}

	// Locking: lock
	private void addKeys(Map<ContactId, TransportKeys> m) {
		for (Entry<ContactId, TransportKeys> e : m.entrySet())
			addKeys(e.getKey(), new MutableTransportKeys(e.getValue()));
	}

	// Locking: lock
	private void addKeys(ContactId c, MutableTransportKeys m) {
		encodeTags(c, m.getPreviousIncomingKeys());
		encodeTags(c, m.getCurrentIncomingKeys());
		encodeTags(c, m.getNextIncomingKeys());
		outContexts.put(c, m.getCurrentOutgoingKeys());
		keys.put(c, m);
	}

	// Locking: lock
	private void encodeTags(ContactId c, MutableIncomingKeys inKeys) {
		for (long streamNumber : inKeys.getWindow().getUnseen()) {
			TagContext tagCtx = new TagContext(c, inKeys, streamNumber);
			byte[] tag = new byte[TAG_LENGTH];
			crypto.encodeTag(tag, inKeys.getTagKey(), streamNumber);
			inContexts.put(new Bytes(tag), tagCtx);
		}
	}

	private void scheduleKeyRotation(long now) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				rotateKeys();
			}
		};
		long delay = rotationPeriodLength - now % rotationPeriodLength;
		scheduler.schedule(task, delay, MILLISECONDS);
	}

	private void rotateKeys() {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Transaction txn = db.startTransaction(false);
					try {
						rotateKeys(txn);
						db.commitTransaction(txn);
					} finally {
						db.endTransaction(txn);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
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
			TransportKeys k = crypto.deriveTransportKeys(transportId, master,
					rotationPeriod, alice);
			// Rotate the keys to the current rotation period if necessary
			rotationPeriod = clock.currentTimeMillis() / rotationPeriodLength;
			k = crypto.rotateTransportKeys(k, rotationPeriod);
			// Initialise mutable state for the contact
			addKeys(c, new MutableTransportKeys(k));
			// Write the keys back to the DB
			db.addTransportKeys(txn, c, k);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void removeContact(ContactId c) {
		lock.lock();
		try {
			// Remove mutable state for the contact
			Iterator<Entry<Bytes, TagContext>> it =
					inContexts.entrySet().iterator();
			while (it.hasNext())
				if (it.next().getValue().contactId.equals(c)) it.remove();
			outContexts.remove(c);
			keys.remove(c);
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
			MutableOutgoingKeys outKeys = outContexts.get(c);
			if (outKeys == null) return null;
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
				crypto.encodeTag(addTag, inKeys.getTagKey(), streamNumber);
				inContexts.put(new Bytes(addTag), new TagContext(
						tagCtx.contactId, inKeys, streamNumber));
			}
			// Remove tags for any stream numbers removed from the window
			for (long streamNumber : change.getRemoved()) {
				if (streamNumber == tagCtx.streamNumber) continue;
				byte[] removeTag = new byte[TAG_LENGTH];
				crypto.encodeTag(removeTag, inKeys.getTagKey(), streamNumber);
				inContexts.remove(new Bytes(removeTag));
			}
			// Write the window back to the DB
			db.setReorderingWindow(txn, tagCtx.contactId, transportId,
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
			Map<ContactId, TransportKeys> snapshot =
					new HashMap<ContactId, TransportKeys>();
			for (Entry<ContactId, MutableTransportKeys> e : keys.entrySet())
				snapshot.put(e.getKey(), e.getValue().snapshot());
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

		private final ContactId contactId;
		private final MutableIncomingKeys inKeys;
		private final long streamNumber;

		private TagContext(ContactId contactId, MutableIncomingKeys inKeys,
				long streamNumber) {
			this.contactId = contactId;
			this.inKeys = inKeys;
			this.streamNumber = streamNumber;
		}
	}

	private static class RotationResult {

		private final Map<ContactId, TransportKeys> current, rotated;

		private RotationResult() {
			current = new HashMap<ContactId, TransportKeys>();
			rotated = new HashMap<ContactId, TransportKeys>();
		}
	}
}
