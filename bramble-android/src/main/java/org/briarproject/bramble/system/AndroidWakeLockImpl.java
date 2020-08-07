package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.FINE;
import static java.util.logging.Logger.getLogger;

/**
 * A wrapper around a {@link SharedWakeLock} that provides the more convenient
 * semantics of {@link AndroidWakeLock} (i.e. calls to acquire() and release()
 * don't need to be balanced).
 */
@ThreadSafe
@NotNullByDefault
class AndroidWakeLockImpl implements AndroidWakeLock {

	private static final Logger LOG =
			getLogger(AndroidWakeLockImpl.class.getName());

	private static final AtomicInteger INSTANCE_ID = new AtomicInteger(0);

	private final SharedWakeLock sharedWakeLock;
	private final String tag;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private boolean held = false;

	AndroidWakeLockImpl(SharedWakeLock sharedWakeLock, String tag) {
		this.sharedWakeLock = sharedWakeLock;
		this.tag = tag + "_" + INSTANCE_ID.getAndIncrement();
	}

	@Override
	public void acquire() {
		synchronized (lock) {
			if (held) {
				if (LOG.isLoggable(FINE)) {
					LOG.fine(tag + " already acquired");
				}
			} else {
				if (LOG.isLoggable(FINE)) {
					LOG.fine(tag + " acquiring shared wake lock");
				}
				held = true;
				sharedWakeLock.acquire();
			}
		}
	}

	@Override
	public void release() {
		synchronized (lock) {
			if (held) {
				if (LOG.isLoggable(FINE)) {
					LOG.fine(tag + " releasing shared wake lock");
				}
				held = false;
				sharedWakeLock.release();
			} else {
				if (LOG.isLoggable(FINE)) {
					LOG.fine(tag + " already released");
				}
			}
		}
	}
}
