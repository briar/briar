package org.briarproject.bramble.util;

public class TimeUtils {

	private static final int NANOS_PER_MILLI = 1000 * 1000;

	/**
	 * Returns the elapsed time in milliseconds since some arbitrary
	 * starting time. This is only useful for measuring elapsed time.
	 */
	public static long now() {
		return System.nanoTime() / NANOS_PER_MILLI;
	}
}
