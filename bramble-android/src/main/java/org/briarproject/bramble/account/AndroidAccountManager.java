package org.briarproject.bramble.account;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;

import java.io.File;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.deleteFileOrDir;

class AndroidAccountManager extends AccountManagerImpl
		implements AccountManager {

	private static final Logger LOG =
			getLogger(AndroidAccountManager.class.getName());

	private static final String PREF_DB_KEY = "key";

	protected final Context appContext;
	private final SharedPreferences prefs;

	@Inject
	AndroidAccountManager(DatabaseConfig databaseConfig,
			CryptoComponent crypto, IdentityManager identityManager,
			SharedPreferences prefs, Application app) {
		super(databaseConfig, crypto, identityManager);
		this.prefs = prefs;
		appContext = app.getApplicationContext();
	}

	@GuardedBy("stateChangeLock")
	@Override
	@Nullable
	protected String loadEncryptedDatabaseKey() {
		String key = getDatabaseKeyFromPreferences();
		if (key == null) key = super.loadEncryptedDatabaseKey();
		else migrateDatabaseKeyToFile(key);
		return key;
	}

	@GuardedBy("stateChangeLock")
	@Nullable
	private String getDatabaseKeyFromPreferences() {
		String key = prefs.getString(PREF_DB_KEY, null);
		if (key == null) LOG.info("No database key in preferences");
		else LOG.info("Found database key in preferences");
		return key;
	}

	@GuardedBy("stateChangeLock")
	private void migrateDatabaseKeyToFile(String key) {
		if (storeEncryptedDatabaseKey(key)) {
			if (prefs.edit().remove(PREF_DB_KEY).commit())
				LOG.info("Database key migrated to file");
			else LOG.warning("Database key not removed from preferences");
		} else {
			LOG.warning("Database key not migrated to file");
		}
	}

	@Override
	public void deleteAccount() {
		synchronized (stateChangeLock) {
			super.deleteAccount();
			SharedPreferences defaultPrefs = getDefaultSharedPreferences();
			deleteAppData(prefs, defaultPrefs);
		}
	}

	// Package access for testing
	SharedPreferences getDefaultSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(appContext);
	}

	@GuardedBy("stateChangeLock")
	private void deleteAppData(SharedPreferences... clear) {
		// Clear and commit shared preferences
		for (SharedPreferences prefs : clear) {
			if (!prefs.edit().clear().commit())
				LOG.warning("Could not clear shared preferences");
		}
		// Delete files, except lib and shared_prefs directories
		File dataDir = new File(appContext.getApplicationInfo().dataDir);
		File[] children = dataDir.listFiles();
		if (children == null) {
			LOG.warning("Could not list files in app data dir");
		} else {
			for (File child : children) {
				String name = child.getName();
				if (!name.equals("lib") && !name.equals("shared_prefs")) {
					deleteFileOrDir(child);
				}
			}
		}
		// Recreate the cache dir as some OpenGL drivers expect it to exist
		if (!new File(dataDir, "cache").mkdir())
			LOG.warning("Could not recreate cache dir");
	}
}
