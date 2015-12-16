package org.briarproject.transport;

import org.briarproject.api.Bytes;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.Timer;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TransportKeys;
import org.briarproject.transport.ReorderingWindow.Change;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

class TransportKeyManager extends TimerTask {

	private static final Logger LOG =
			Logger.getLogger(TransportKeyManager.class.getName());

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final Executor dbExecutor;
	private final Timer timer;
	private final Clock clock;
	private final TransportId transportId;
	private final long rotationPeriodLength;
	private final ReentrantLock lock;

	// The following are locking: lock
	private final Map<Bytes, TagContext> inContexts;
	private final Map<ContactId, MutableOutgoingKeys> outContexts;
	private final Map<ContactId, MutableTransportKeys> keys;

	TransportKeyManager(DatabaseComponent db, CryptoComponent crypto,
			Executor dbExecutor, Timer timer, Clock clock,
			TransportId transportId, long maxLatency) {
		this.db = db;
		this.crypto = crypto;
		this.dbExecutor = dbExecutor;
		this.timer = timer;
		this.clock = clock;
		this.transportId = transportId;
		rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
		lock = new ReentrantLock();
		inContexts = new HashMap<Bytes, TagContext>();
		outContexts = new HashMap<ContactId, MutableOutgoingKeys>();
		keys = new HashMap<ContactId, MutableTransportKeys>();
	}

	void start() {
		// Load the transport keys from the DB
		Map<ContactId, TransportKeys> loaded;
		try {
			loaded = db.getTransportKeys(transportId);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return;
		}
		// Rotate the keys to the current rotation period
		Map<ContactId, TransportKeys> rotated =
				new HashMap<ContactId, TransportKeys>();
		Map<ContactId, TransportKeys> current =
				new HashMap<ContactId, TransportKeys>();
		long now = clock.currentTimeMillis();
		long rotationPeriod = now / rotationPeriodLength;
		for (Entry<ContactId, TransportKeys> e : loaded.entrySet()) {
			ContactId c = e.getKey();
			TransportKeys k = e.getValue();
			TransportKeys k1 = crypto.rotateTransportKeys(k, rotationPeriod);
			if (k1.getRotationPeriod() > k.getRotationPeriod())
				rotated.put(c, k1);
			current.put(c, k1);
		}
		lock.lock();
		try {
			// Initialise mutable state for all contacts
			for (Entry<ContactId, TransportKeys> e : current.entrySet())
				addKeys(e.getKey(), new MutableTransportKeys(e.getValue()));
			// Write any rotated keys back to the DB
			saveTransportKeys(rotated);
		} finally {
			lock.unlock();
		}
		// Schedule a periodic task to rotate the keys
		long delay = rotationPeriodLength - now % rotationPeriodLength;
		timer.scheduleAtFixedRate(this, delay, rotationPeriodLength);
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

	private void saveTransportKeys(final Map<ContactId, TransportKeys> rotated) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					db.updateTransportKeys(rotated);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	void addContact(ContactId c, TransportKeys k) {
		lock.lock();
		try {
			// Initialise mutable state for the contact
			addKeys(c, new MutableTransportKeys(k));
		} finally {
			lock.unlock();
		}
	}

	void removeContact(ContactId c) {
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

	StreamContext getStreamContext(ContactId c) {
		StreamContext ctx;
		lock.lock();
		try {
			// Look up the outgoing keys for the contact
			MutableOutgoingKeys outKeys = outContexts.get(c);
			if (outKeys == null) return null;
			if (outKeys.getStreamCounter() > MAX_32_BIT_UNSIGNED) return null;
			// Create a stream context
			ctx = new StreamContext(c, transportId, outKeys.getTagKey(),
					outKeys.getHeaderKey(), outKeys.getStreamCounter());
			// Increment the stream counter and write it back to the DB
			outKeys.incrementStreamCounter();
			saveIncrementedStreamCounter(c, outKeys.getRotationPeriod());
		} finally {
			lock.unlock();
		}
		// TODO: Wait for save to complete, return null if it fails
		return ctx;
	}

	private void saveIncrementedStreamCounter(final ContactId c,
			final long rotationPeriod) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					db.incrementStreamCounter(c, transportId, rotationPeriod);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	StreamContext recogniseTag(byte[] tag) {
		StreamContext ctx;
		lock.lock();
		try {
			// Look up the incoming keys for the tag
			TagContext tagCtx = inContexts.remove(new Bytes(tag));
			if (tagCtx == null) return null;
			MutableIncomingKeys inKeys = tagCtx.inKeys;
			// Create a stream context
			ctx = new StreamContext(tagCtx.contactId, transportId,
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
				byte[] removeTag = new byte[TAG_LENGTH];
				crypto.encodeTag(removeTag, inKeys.getTagKey(), streamNumber);
				inContexts.remove(new Bytes(removeTag));
			}
			// Write the window back to the DB
			saveReorderingWindow(tagCtx.contactId, inKeys.getRotationPeriod(),
					window.getBase(), window.getBitmap());
		} finally {
			lock.unlock();
		}
		// TODO: Wait for save to complete, return null if it fails
		return ctx;
	}

	private void saveReorderingWindow(final ContactId c,
			final long rotationPeriod, final long base, final byte[] bitmap) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					db.setReorderingWindow(c, transportId, rotationPeriod,
							base, bitmap);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void run() {
		lock.lock();
		try {
			// Rotate the keys to the current rotation period
			Map<ContactId, TransportKeys> rotated =
					new HashMap<ContactId, TransportKeys>();
			Map<ContactId, TransportKeys> current =
					new HashMap<ContactId, TransportKeys>();
			long now = clock.currentTimeMillis();
			long rotationPeriod = now / rotationPeriodLength;
			for (Entry<ContactId, MutableTransportKeys> e : keys.entrySet()) {
				ContactId c = e.getKey();
				TransportKeys k = e.getValue().snapshot();
				TransportKeys k1 = crypto.rotateTransportKeys(k,
						rotationPeriod);
				if (k1.getRotationPeriod() > k.getRotationPeriod())
					rotated.put(c, k1);
				current.put(c, k1);
			}
			// Rebuild the mutable state for all contacts
			inContexts.clear();
			outContexts.clear();
			keys.clear();
			for (Entry<ContactId, TransportKeys> e : current.entrySet())
				addKeys(e.getKey(), new MutableTransportKeys(e.getValue()));
			// Write any rotated keys back to the DB
			saveTransportKeys(rotated);
		} finally {
			lock.unlock();
		}
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
}
