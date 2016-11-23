package org.briarproject.bramble.api.settings;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface SettingsManager {

	/**
	 * Returns all settings in the given namespace.
	 */
	Settings getSettings(String namespace) throws DbException;

	/**
	 * Merges the given settings with any existing settings in the given
	 * namespace.
	 */
	void mergeSettings(Settings s, String namespace) throws DbException;
}
