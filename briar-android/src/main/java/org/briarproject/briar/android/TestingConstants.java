package org.briarproject.briar.android;

import org.briarproject.briar.BuildConfig;

import java.util.logging.Level;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;

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
	 * Default log level. Disable logging for final release builds.
	 */
	@SuppressWarnings("ConstantConditions")
	Level DEFAULT_LOG_LEVEL = IS_DEBUG_BUILD || IS_BETA_BUILD ? INFO : OFF;

	/**
	 * Whether to prevent screenshots from being taken. Setting this to true
	 * prevents Recent Apps from storing screenshots of private information.
	 * Unfortunately this also prevents the user from taking screenshots
	 * intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = !IS_DEBUG_BUILD;
}
