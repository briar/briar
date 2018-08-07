package org.briarproject.briar.android.account;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.android.LockManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.settings.SettingsFragment.PREF_SCREEN_LOCK;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static org.briarproject.briar.android.util.UiUtils.hasScreenLock;

@ThreadSafe
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class LockManagerImpl implements LockManager {

	private static final Logger LOG =
			Logger.getLogger(LockManagerImpl.class.getName());

	private final Context appContext;
	private final SettingsManager settingsManager;
	private final AndroidNotificationManager notificationManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private volatile boolean locked = false;
	private final MutableLiveData<Boolean> lockable = new MutableLiveData<>();

	@Inject
	public LockManagerImpl(Application app, SettingsManager settingsManager,
			AndroidNotificationManager notificationManager,
			@DatabaseExecutor Executor dbExecutor) {
		this.appContext = app.getApplicationContext();
		this.settingsManager = settingsManager;
		this.notificationManager = notificationManager;
		this.dbExecutor = dbExecutor;

		// setting this in the constructor makes #getValue() @NonNull
		this.lockable.setValue(false);
	}

	@Override
	public LiveData<Boolean> isLockable() {
		return lockable;
	}

	@UiThread
	@Override
	public void checkIfLockable() {
		boolean oldValue = lockable.getValue();
		dbExecutor.execute(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				boolean lockable =
						settings.getBoolean(PREF_SCREEN_LOCK, false);
				boolean newValue = hasScreenLock(appContext) && lockable;
				if (oldValue != newValue) {
					this.lockable.postValue(newValue);
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				this.lockable.postValue(false);
			}
		});
	}

	@Override
	public boolean isLocked() {
		return locked;
	}

	@Override
	public void setLocked(boolean locked) {
		this.locked = locked;
		notificationManager.updateForegroundNotification(locked);
	}
}
