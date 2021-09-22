package org.briarproject.bramble.api.logging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.Handler;
import java.util.logging.Logger;

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
	 */
	Handler createLogHandler(File dir) throws IOException;

	/**
	 * Creates a persistent log handler that stores its logs in the given
	 * directory and adds the handler to the given logger, replacing any
	 * existing persistent log handler.
	 */
	void addLogHandler(File dir, Logger logger) throws IOException;

	/**
	 * Returns a {@link Scanner} for reading the persistent log entries stored
	 * in the given directory.
	 *
	 * @param old True if the previous session's log should be loaded, or false
	 * if the current session's log should be loaded
	 */
	Scanner getPersistentLog(File dir, boolean old) throws IOException;
}
