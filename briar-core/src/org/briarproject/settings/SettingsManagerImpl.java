package org.briarproject.settings;

import com.google.inject.Inject;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;

class SettingsManagerImpl implements SettingsManager {

	private final DatabaseComponent db;

	@Inject
	SettingsManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public Settings getSettings(String namespace) throws DbException {
		return db.getSettings(namespace);
	}

	@Override
	public void mergeSettings(Settings s, String namespace) throws DbException {
		db.mergeSettings(s, namespace);
	}
}
