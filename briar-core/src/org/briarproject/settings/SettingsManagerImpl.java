package org.briarproject.settings;

import com.google.inject.Inject;

import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.Settings;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import java.util.logging.Logger;

import java.util.Collection;

class SettingsManagerImpl implements SettingsManager {

	private final DatabaseComponent db;
	private static final Logger LOG =
			Logger.getLogger("SettingsManagerImpl");

	@Inject
	SettingsManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	/**
	* Returns the settings object identified by the provided namespace
	* string
	*/
	@Override
	public Settings getSettings(String namespace) throws DbException {
		return db.getSettings(namespace);
	}

	/**
	* Merges (read syncs) the provided settings identified by the provided namespace
	* string
	*/
	@Override
	public void mergeSettings(Settings s, String namespace) throws DbException {
		db.mergeSettings(s, namespace);
	}


}
