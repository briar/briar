package org.briarproject.android;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;

import java.util.logging.Level;

public interface TestingConstants {

	/**
	 * Whether this is an alpha or beta build. This should be set to false for
	 * release builds.
	 */
	boolean TESTING = true;

	/** Default log level. */
	Level DEFAULT_LOG_LEVEL = TESTING ? INFO : OFF;

	/**
	 * Whether to prevent screenshots from being taken. Setting this to true
	 * prevents Recent Apps from storing screenshots of private information.
	 * Unfortunately this also prevents the user from taking screenshots
	 * intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = TESTING ? false : true;

	/**
	 * Whether to allow TestingActivity to be launched from SettingsFragment.
	 */
	boolean SHOW_TESTING_ACTIVITY = TESTING ? true : false;

	/** Whether to allow crash reports to be submitted by email. */
	boolean SHARE_CRASH_REPORTS = TESTING ? true : false;
}
