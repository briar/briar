package org.briarproject.settings;


import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;

import javax.inject.Inject;

class SettingsManagerImpl implements SettingsManager {

	private final DatabaseComponent db;

	@Inject
	SettingsManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public Settings getSettings(String namespace) throws DbException {
		Settings s;
		Transaction txn = db.startTransaction(true);
		try {
			s = db.getSettings(txn, namespace);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return s;
	}

	@Override
	public void mergeSettings(Settings s, String namespace) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			db.mergeSettings(txn, s, namespace);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}
}
