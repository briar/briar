package org.briarproject.android.controller;

import android.content.SharedPreferences;

import org.briarproject.api.db.DatabaseConfig;

import javax.inject.Inject;

public class ConfigControllerImpl implements ConfigController {

	private final static String PREF_DB_KEY = "key";

	@Inject
	protected SharedPreferences briarPrefs;
	@Inject
	protected volatile DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl() {

	}

	public String getEncryptedDatabaseKey() {
		return briarPrefs.getString(PREF_DB_KEY, null);
	}

	public void clearPrefs() {
		SharedPreferences.Editor editor = briarPrefs.edit();
		editor.clear();
		editor.apply();
	}

	@Override
	public boolean initialized() {
		String hex = getEncryptedDatabaseKey();
		if (hex != null && databaseConfig.databaseExists()) {
			return true;
		}
		return false;
	}
}
