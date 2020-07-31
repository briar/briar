package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface AlarmConstants {

	/**
	 * Request code for the broadcast intent attached to the periodic alarm.
	 */
	int REQUEST_ALARM = 1;

	/**
	 * Key for storing the process ID in the extras of the periodic alarm's
	 * intent. This allows us to ignore alarms scheduled by dead processes.
	 */
	String EXTRA_PID = "org.briarproject.bramble.EXTRA_PID";
}
