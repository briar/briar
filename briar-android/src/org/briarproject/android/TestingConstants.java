package org.briarproject.android;

import static java.util.logging.Level.INFO;

import java.util.logging.Level;

interface TestingConstants {

	/** Default log level - this should be OFF for release builds. */
	Level DEFAULT_LOG_LEVEL = INFO;

	/**
	 * Whether to prevent screenshots from being taken. This should be true for
	 * release builds, to prevent Recent Apps from storing screenshots of
	 * private information. Unfortunately this also prevents the user from
	 * taking screenshots intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = false;

	/**
	 * Whether to allow TestingActivity to be launched from SettingsActivity.
	 * This should be false for release builds.
	 */
	boolean SHOW_TESTING_ACTIVITY = true;

	/**
	 * Whether to allow crash reports to be submitted by email. This should
	 * be false for release builds.
	 */
	boolean SHARE_CRASH_REPORTS = true;
}
