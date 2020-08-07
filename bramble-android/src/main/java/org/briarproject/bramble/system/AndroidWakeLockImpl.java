package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;

import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

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

	private final SharedWakeLock sharedWakeLock;
	private final Object lock = new Object();
	@GuardedBy("lock")
	private boolean held = false;

	AndroidWakeLockImpl(SharedWakeLock sharedWakeLock) {
		this.sharedWakeLock = sharedWakeLock;
	}

	@Override
	public void acquire() {
		synchronized (lock) {
			if (held) {
				LOG.fine("Already acquired");
			} else {
				LOG.fine("Acquiring shared wake lock");
				held = true;
				sharedWakeLock.acquire();
			}
		}
	}

	@Override
	public void release() {
		synchronized (lock) {
			if (held) {
				LOG.fine("Releasing shared wake lock");
				held = false;
				sharedWakeLock.release();
			} else {
				LOG.fine("Already released");
			}
		}
	}
}
