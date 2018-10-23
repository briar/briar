package org.briarproject.briar.api.logging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
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
	 */
	Collection<String> getPersistedLog(File dir) throws IOException;
}
