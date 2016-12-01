package org.briarproject.briar.android.controller;

import android.content.Context;
import android.content.SharedPreferences;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.AndroidUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

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
		return briarPrefs.getString(PREF_DB_KEY, null);
	}

	@Override
	public void storeEncryptedDatabaseKey(String hex) {
		SharedPreferences.Editor editor = briarPrefs.edit();
		editor.putString(PREF_DB_KEY, hex);
		editor.apply();
	}

	@Override
	public void deleteAccount(Context ctx) {
		SharedPreferences.Editor editor = briarPrefs.edit();
		editor.clear();
		editor.apply();
		AndroidUtils.deleteAppData(ctx);
	}

	@Override
	public boolean accountExists() {
		String hex = getEncryptedDatabaseKey();
		return hex != null && databaseConfig.databaseExists();
	}
}
