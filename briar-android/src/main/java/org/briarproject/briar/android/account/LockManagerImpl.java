package org.briarproject.briar.android.account;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
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
public class LockManagerImpl implements LockManager, Service, EventListener {

	private static final Logger LOG =
			Logger.getLogger(LockManagerImpl.class.getName());

	private final Context appContext;
	private final SettingsManager settingsManager;
	private final AndroidNotificationManager notificationManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private final MutableLiveData<Boolean> locked = new MutableLiveData<>();
	private final MutableLiveData<Boolean> lockable = new MutableLiveData<>();

	@Inject
	public LockManagerImpl(Application app, SettingsManager settingsManager,
			AndroidNotificationManager notificationManager,
			@DatabaseExecutor Executor dbExecutor) {
		this.appContext = app.getApplicationContext();
		this.settingsManager = settingsManager;
		this.notificationManager = notificationManager;
		this.dbExecutor = dbExecutor;

		// setting these in the constructor makes #getValue() @NonNull
		this.locked.setValue(false);
		this.lockable.setValue(false);
	}

	@Override
	public void startService() {
		lockable.observeForever(this::onLockableChanged);
		if (hasScreenLock(appContext)) {
			loadLockableSetting();
		} else {
			lockable.postValue(false);
		}
	}

	@Override
	public void stopService() {
		lockable.removeObserver(this::onLockableChanged);
	}

	@Override
	public void eventOccurred(Event event) {
		if (event instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent e = (SettingsUpdatedEvent) event;
			String namespace = e.getNamespace();
			if (namespace.equals(SETTINGS_NAMESPACE)) {
				loadLockableSetting();
			}
		}
	}

	private void loadLockableSetting() {
		dbExecutor.execute(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				boolean lockable =
						settings.getBoolean(PREF_SCREEN_LOCK, false);
				boolean newValue = hasScreenLock(appContext) && lockable;
				this.lockable.postValue(newValue);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				this.lockable.postValue(false);
			}
		});
	}

	private void onLockableChanged(boolean lockable) {
		notificationManager
				.updateForegroundNotification(lockable, locked.getValue());
	}

	@Override
	public LiveData<Boolean> isLockable() {
		return lockable;
	}

	@UiThread
	@Override
	public void recheckLockable() {
		boolean oldValue = this.lockable.getValue();
		boolean newValue = hasScreenLock(appContext) && lockable.getValue();
		if (oldValue != newValue) {
			this.lockable.setValue(newValue);
		}
	}

	@Override
	public LiveData<Boolean> isLocked() {
		return locked;
	}

	@Override
	public void setLocked(boolean locked) {
		this.locked.setValue(locked);
		notificationManager
				.updateForegroundNotification(lockable.getValue(), locked);
	}
}
