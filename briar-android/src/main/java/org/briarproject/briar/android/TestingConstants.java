package org.briarproject.briar.android;

import org.briarproject.briar.BuildConfig;

import java.util.logging.Level;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;

public interface TestingConstants {

	/**
	 * Whether this is an alpha or beta build. This should be set to false for
	 * release builds.
	 */
	boolean TESTING = BuildConfig.DEBUG;

	/** Default log level. */
	Level DEFAULT_LOG_LEVEL = TESTING ? INFO : OFF;

	/**
	 * Whether to prevent screenshots from being taken. Setting this to true
	 * prevents Recent Apps from storing screenshots of private information.
	 * Unfortunately this also prevents the user from taking screenshots
	 * intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = !TESTING;
}
