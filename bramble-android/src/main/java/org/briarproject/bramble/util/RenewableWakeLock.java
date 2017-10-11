package org.briarproject.bramble.util;

import android.os.PowerManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

public class RenewableWakeLock {

	private static final Logger LOG =
			Logger.getLogger(RenewableWakeLock.class.getName());

	private final PowerManager powerManager;
	private final ScheduledExecutorService scheduler;
	private final int levelAndFlags;
	private final String tag;
	private final long duration;
	private final TimeUnit timeUnit;
	private final Runnable renewTask;

	private final Object lock = new Object();
	private PowerManager.WakeLock wakeLock; // Locking: lock
	private ScheduledFuture future; // Locking: lock

	public RenewableWakeLock(PowerManager powerManager,
			ScheduledExecutorService scheduler, int levelAndFlags, String tag,
			long duration, TimeUnit timeUnit) {
		this.powerManager = powerManager;
		this.scheduler = scheduler;
		this.levelAndFlags = levelAndFlags;
		this.tag = tag;
		this.duration = duration;
		this.timeUnit = timeUnit;
		renewTask = this::renew;
	}

	public void acquire() {
		if (LOG.isLoggable(INFO)) LOG.info("Acquiring wake lock " + tag);
		synchronized (lock) {
			if (wakeLock != null) {
				LOG.info("Already acquired");
				return;
			}
			wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire();
			future = scheduler.schedule(renewTask, duration, timeUnit);
		}
	}

	private void renew() {
		if (LOG.isLoggable(INFO)) LOG.info("Renewing wake lock " + tag);
		synchronized (lock) {
			if (wakeLock == null) {
				LOG.info("Already released");
				return;
			}
			PowerManager.WakeLock oldWakeLock = wakeLock;
			wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire();
			oldWakeLock.release();
			future = scheduler.schedule(renewTask, duration, timeUnit);
		}
	}

	public void release() {
		if (LOG.isLoggable(INFO)) LOG.info("Releasing wake lock " + tag);
		synchronized (lock) {
			if (wakeLock == null) {
				LOG.info("Already released");
				return;
			}
			future.cancel(false);
			future = null;
			wakeLock.release();
			wakeLock = null;
		}
	}
}

