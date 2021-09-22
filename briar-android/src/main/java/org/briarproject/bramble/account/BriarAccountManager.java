package org.briarproject.bramble.account;

import android.app.Application;
import android.content.SharedPreferences;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.logging.PersistentLogManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.util.UiUtils;

import javax.inject.Inject;

class BriarAccountManager extends AndroidAccountManager {

	@Inject
	BriarAccountManager(
			DatabaseConfig databaseConfig,
			CryptoComponent crypto,
			IdentityManager identityManager,
			SharedPreferences prefs,
			PersistentLogManager logManager,
			FeatureFlags featureFlags,
			Application app) {
		super(databaseConfig, crypto, identityManager, prefs, logManager,
				featureFlags, app);
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
