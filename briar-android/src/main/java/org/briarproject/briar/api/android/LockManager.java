package org.briarproject.briar.api.android;

import android.arch.lifecycle.LiveData;

public interface LockManager {

	LiveData<Boolean> isLockable();

	void recheckLockable();

	void updateLockableSetting(boolean lockable);

	LiveData<Boolean>  isLocked();

	void setLocked(boolean locked);

}
