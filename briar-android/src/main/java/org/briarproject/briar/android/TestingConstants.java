package org.briarproject.briar.android;

import org.briarproject.briar.BuildConfig;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.concurrent.TimeUnit.DAYS;

public interface TestingConstants {

	/**
	 * Whether this is a debug build.
	 */
	boolean IS_DEBUG_BUILD = BuildConfig.DEBUG;

	/**
	 * Whether to prevent screenshots from being taken. Setting this to true
	 * prevents Recent Apps from storing screenshots of private information.
	 * Unfortunately this also prevents the user from taking screenshots
	 * intentionally.
	 */
	boolean PREVENT_SCREENSHOTS = !IS_DEBUG_BUILD;

	boolean IS_OLD_ANDROID = SDK_INT <= 19;
	long OLD_ANDROID_WARN_DATE = 1659225600_000L;   // 2022-07-31
	long OLD_ANDROID_EXPIRY_DATE = 1675123200_000L; // 2023-01-31

	/**
	 * Debug builds expire after 90 days. Release builds running on Android 4
	 * expire at a set date, otherwise they expire after 292 million years.
	 */
	long EXPIRY_DATE = IS_DEBUG_BUILD ?
			BuildConfig.BuildTimestamp + DAYS.toMillis(90)
			: (IS_OLD_ANDROID ? OLD_ANDROID_EXPIRY_DATE : Long.MAX_VALUE);
}
