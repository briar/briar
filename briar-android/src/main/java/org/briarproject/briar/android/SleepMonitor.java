package org.briarproject.briar.android;

import android.os.SystemClock;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SleepMonitor implements Runnable {

	/**
	 * How often to check the uptime and real time.
	 */
	private static final int INTERVAL_MS = 5000;

	/**
	 * If the difference between uptime and real time changes by more than this amount, assume deep
	 * sleep has occurred.
	 */
	private static final int MIN_SLEEP_DURATION_MS = 1000;

	private final ScheduledExecutorService executorService;

	private volatile long uptime, realtime, diff;

	SleepMonitor() {
		uptime = SystemClock.uptimeMillis();
		realtime = SystemClock.elapsedRealtime();
		diff = realtime - uptime;
		executorService = Executors.newSingleThreadScheduledExecutor();
	}

	void start() {
		executorService.scheduleAtFixedRate(this, 0, INTERVAL_MS, MILLISECONDS);
	}

	@Override
	public void run() {
		long sleepDuration = getSleepDuration();
		if (sleepDuration > MIN_SLEEP_DURATION_MS) {
			String start = getTime(System.currentTimeMillis() - sleepDuration);
			Log.i("SLEEP_INFO", "System slept for " + sleepDuration + " ms (since " + start + ")");
		}
	}

	/**
	 * Returns the amount of time spent in deep sleep since the last check.
	 */
	private long getSleepDuration() {
		uptime = SystemClock.uptimeMillis();
		realtime = SystemClock.elapsedRealtime();
		long lastDiff = diff;
		diff = realtime - uptime;
		return diff - lastDiff;
	}

	private String getTime(long time) {
		DateFormat sdf = new SimpleDateFormat("HH:mm:ss", US);
		return sdf.format(new Date(time));
	}
}
