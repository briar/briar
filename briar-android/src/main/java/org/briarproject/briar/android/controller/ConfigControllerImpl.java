package org.briarproject.briar.android.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.AndroidUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

	private static final Logger LOG =
			Logger.getLogger(ConfigControllerImpl.class.getName());

	private static final String PREF_DB_KEY = "key";

	private final SharedPreferences briarPrefs;
	protected final AccountManager accountManager;
	protected final DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl(SharedPreferences briarPrefs,
			AccountManager accountManager, DatabaseConfig databaseConfig) {
		this.briarPrefs = briarPrefs;
		this.accountManager = accountManager;
		this.databaseConfig = databaseConfig;
	}

	@Override
	@Nullable
	public String getEncryptedDatabaseKey() {
		String key = getDatabaseKeyFromPreferences();
		if (key == null) key = accountManager.getEncryptedDatabaseKey();
		else migrateDatabaseKeyToFile(key);
		return key;
	}

	@Nullable
	private String getDatabaseKeyFromPreferences() {
		String key = briarPrefs.getString(PREF_DB_KEY, null);
		if (key == null) LOG.info("No database key in preferences");
		else LOG.info("Found database key in preferences");
		return key;
	}

	private void migrateDatabaseKeyToFile(String key) {
		if (accountManager.storeEncryptedDatabaseKey(key)) {
			if (briarPrefs.edit().remove(PREF_DB_KEY).commit())
				LOG.info("Database key migrated to file");
			else LOG.warning("Database key not removed from preferences");
		} else {
			LOG.warning("Database key not migrated to file");
		}
	}

	@Override
	public boolean storeEncryptedDatabaseKey(String hex) {
		return accountManager.storeEncryptedDatabaseKey(hex);
	}

	@Override
	public void deleteAccount(Context ctx) {
		LOG.info("Deleting account");
		SharedPreferences defaultPrefs =
				PreferenceManager.getDefaultSharedPreferences(ctx);
		AndroidUtils.deleteAppData(ctx, briarPrefs, defaultPrefs);
	}

	@Override
	public boolean accountExists() {
		return getEncryptedDatabaseKey() != null &&
				databaseConfig.getDatabaseDirectory().isDirectory();
	}

	@Override
	public boolean accountSignedIn() {
		return accountManager.hasDatabaseKey();
	}
}
