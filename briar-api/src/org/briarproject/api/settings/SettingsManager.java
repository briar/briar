package org.briarproject.api.settings;

import org.briarproject.api.db.DbException;
import org.briarproject.api.Settings;

public interface SettingsManager {

	/**
	* Returns the settings object identified by the provided namespace
	* string
	*/
	Settings getSettings(String namespace) throws DbException;

	/**
	* Merges (read syncs) the provided settings identified by the provided namespace
	* string
	*/
	void mergeSettings(Settings s, String namespace) throws DbException;

}
