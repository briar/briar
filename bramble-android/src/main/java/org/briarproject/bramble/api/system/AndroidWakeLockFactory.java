package org.briarproject.bramble.api.system;

import android.os.PowerManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AndroidWakeLockFactory {

	/**
	 * Creates and returns a wake lock.
	 *
	 * @param levelAndFlags See {@link PowerManager#newWakeLock(int, String)}
	 */
	AndroidWakeLock createWakeLock(int levelAndFlags);
}
