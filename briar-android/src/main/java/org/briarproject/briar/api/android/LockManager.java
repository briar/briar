package org.briarproject.briar.api.android;

import android.arch.lifecycle.LiveData;
import android.support.annotation.UiThread;

public interface LockManager {

	/**
	 * Returns an observable LiveData to indicate whether the app can be locked.
	 */
	LiveData<Boolean> isLockable();

	/**
	 * Updates the LiveData returned by {@link #isLockable()}.
	 * It checks whether a device screen lock is available and
	 * whether the app setting is checked.
	 */
	@UiThread
	void checkIfLockable();

	/**
	 * Returns true if app is currently locked, false otherwise.
	 */
	boolean isLocked();

	/**
	 * Locks the app if true is passed, otherwise unlocks the app.
	 */
	void setLocked(boolean locked);

}
