package org.briarproject.bramble.api.logging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Handler;

@NotNullByDefault
public interface PersistentLogManager {

	/**
	 * The namespace of the (@link Settings) where the log key is stored.
	 */
	String LOG_SETTINGS_NAMESPACE = "log";

	/**
	 * The {@link Settings} key under which the log key is stored.
	 */
	String LOG_KEY_KEY = "logKey";

	/**
	 * Creates and returns a persistent log handler that stores its logs in
	 * the given directory.
	 * <p>
	 * This method should only be called once.
	 */
	Handler createLogHandler(File dir) throws IOException;

	/**
	 * Loads and returns the persistent log entries stored in the given
	 * directory, or an empty list if no log entries are found.
	 *
	 * @param old True if the previous session's log should be loaded, or false
	 * if the current session's log should be loaded
	 */
	List<String> getPersistedLog(File dir, boolean old) throws IOException;
}
