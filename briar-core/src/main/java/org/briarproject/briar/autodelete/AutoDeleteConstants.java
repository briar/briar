package org.briarproject.briar.autodelete;

interface AutoDeleteConstants {

	/**
	 * Group metadata key for storing the auto-delete timer duration.
	 */
	String GROUP_KEY_TIMER = "autoDeleteTimer";

	/**
	 * Group metadata key for storing the timestamp of the latest incoming or
	 * outgoing message carrying an auto-delete timer (including a null timer).
	 */
	String GROUP_KEY_TIMESTAMP = "autoDeleteTimestamp";

	/**
	 * Group metadata key for storing the previous auto-delete timer duration.
	 * This is used to decide whether a local change to the duration should be
	 * overwritten by a duration received from the contact.
	 */
	String GROUP_KEY_PREVIOUS_TIMER = "autoDeletePreviousTimer";

	/**
	 * Special value for {@link #GROUP_KEY_PREVIOUS_TIMER} indicating that
	 * there are no local changes to the auto-delete timer duration that need
	 * to be compared with durations received from the contact.
	 */
	long NO_PREVIOUS_TIMER = 0;
}
