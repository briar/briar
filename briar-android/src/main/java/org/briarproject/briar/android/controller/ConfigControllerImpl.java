package org.briarproject.briar.android.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.AndroidUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

	private static final Logger LOG =
			Logger.getLogger(ConfigControllerImpl.class.getName());

	private static final String PREF_DB_KEY = "key";

	private final SharedPreferences briarPrefs;
	protected final DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig) {
		this.briarPrefs = briarPrefs;
		this.databaseConfig = databaseConfig;
	}

	@Override
	@Nullable
	public String getEncryptedDatabaseKey() {
		String key = briarPrefs.getString(PREF_DB_KEY, null);
		if (LOG.isLoggable(INFO))
			LOG.info("Got database key from preferences: " + (key != null));
		return key;
	}

	@Override
	@SuppressLint("ApplySharedPref")
	public void storeEncryptedDatabaseKey(String hex) {
		LOG.info("Storing database key in preferences");
		briarPrefs.edit().putString(PREF_DB_KEY, hex).commit();
	}

	@Override
	public void deleteAccount(Context ctx) {
		LOG.info("Deleting account");
		SharedPreferences defaultPrefs =
				PreferenceManager.getDefaultSharedPreferences(ctx);
		AndroidUtils.deleteAppData(ctx, briarPrefs, defaultPrefs);
		AndroidUtils.logDataDirContents(ctx);
	}

	@Override
	public boolean accountExists() {
		String hex = getEncryptedDatabaseKey();
		boolean exists = hex != null && databaseConfig.databaseExists();
		if (LOG.isLoggable(INFO)) LOG.info("Account exists: " + exists);
		return exists;
	}

	@Override
	public boolean accountSignedIn() {
		boolean signedIn = databaseConfig.getEncryptionKey() != null;
		if (LOG.isLoggable(INFO)) LOG.info("Signed in: " + signedIn);
		return signedIn;
	}

}
