package org.briarproject.android.controller;

import android.content.Context;
import android.content.SharedPreferences;

import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.db.DatabaseConfig;

import javax.inject.Inject;

public class ConfigControllerImpl implements ConfigController {

	private static final String PREF_DB_KEY = "key";

	@Inject
	protected SharedPreferences briarPrefs;
	@Inject
	protected volatile DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl() {

	}

	@Override
	public String getEncryptedDatabaseKey() {
		return briarPrefs.getString(PREF_DB_KEY, null);
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
