package org.briarproject.briar.android;

import org.briarproject.briar.BuildConfig;

public interface TestingConstants {

	/**
	 * Whether this is a debug build.
	 */
	boolean IS_DEBUG_BUILD = BuildConfig.DEBUG;

	/**
	 * Whether this is a beta build. This should be set to false for final
	 * release builds.
	 */
	boolean IS_BETA_BUILD = false;

	/**
	 * Whether to prevent screenshots from being taken. Setting this to true
	 * prevents Recent Apps from storing screenshots of private information.
	 * Unfortunately this also prevents the user from taking screenshots
	 * intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = !IS_DEBUG_BUILD;

	/**
	 * Debug and beta builds expire after 90 days. Final release builds expire
	 * after 292 million years.
	 */
	long EXPIRY_DATE = IS_DEBUG_BUILD || IS_BETA_BUILD ?
			BuildConfig.BuildTimestamp + 90 * 24 * 60 * 60 * 1000L :
			Long.MAX_VALUE;
}
