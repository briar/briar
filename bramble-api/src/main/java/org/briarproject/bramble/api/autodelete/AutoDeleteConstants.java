package org.briarproject.bramble.api.autodelete;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;

public interface AutoDeleteConstants {

	/**
	 * The minimum valid auto-delete timer duration in milliseconds.
	 */
	long MIN_AUTO_DELETE_TIMER_MS = MINUTES.toMillis(1);

	/**
	 * The maximum valid auto-delete timer duration in milliseconds.
	 */
	long MAX_AUTO_DELETE_TIMER_MS = DAYS.toMillis(365);

	/**
	 * Placeholder value indicating that a message has no auto-delete timer.
	 * This value should not be sent over the wire - send null instead.
	 */
	long NO_AUTO_DELETE_TIMER = -1;
}
