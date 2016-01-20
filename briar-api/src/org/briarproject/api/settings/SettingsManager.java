package org.briarproject.api.settings;

import org.briarproject.api.Settings;
import org.briarproject.api.db.DbException;

public interface SettingsManager {

	/** Returns all settings in the given namespace. */
	Settings getSettings(String namespace) throws DbException;

	/**
	 * Merges the given settings with the existing settings in the given
	 * namespace.
	 */
	void mergeSettings(Settings s, String namespace) throws DbException;
}
