package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AndroidWakeLock {

	/**
	 * Acquires the wake lock. This has no effect if the wake lock has already
	 * been acquired.
	 */
	void acquire();

	/**
	 * Releases the wake lock. This has no effect if the wake lock has already
	 * been released.
	 */
	void release();
}
