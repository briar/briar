package org.briarproject.briar.api.android;

import android.arch.lifecycle.LiveData;
import android.support.annotation.UiThread;

public interface LockManager {

	LiveData<Boolean> isLockable();

	@UiThread
	void recheckLockable();

	LiveData<Boolean>  isLocked();

	void setLocked(boolean locked);

}
