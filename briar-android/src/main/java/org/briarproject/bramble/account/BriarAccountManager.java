package org.briarproject.bramble.account;

import android.app.Application;
import android.content.SharedPreferences;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import javax.inject.Inject;

class BriarAccountManager extends AndroidAccountManager {

	private final AndroidNotificationManager notificationManager;

	@Inject
	BriarAccountManager(DatabaseConfig databaseConfig, CryptoComponent crypto,
			IdentityManager identityManager, SharedPreferences prefs,
			Application app, AndroidNotificationManager notificationManager) {
		super(databaseConfig, crypto, identityManager, prefs, app);
		this.notificationManager = notificationManager;
	}

	@Override
	public void setLocked(boolean locked) {
		super.setLocked(locked);
		notificationManager.updateForegroundNotification(true, locked);
	}

	@Override
	public void deleteAccount() {
		synchronized (stateChangeLock) {
			super.deleteAccount();
			Localizer.reinitialize();
			UiUtils.setTheme(appContext,
					appContext.getString(R.string.pref_theme_light_value));
		}
	}
}
