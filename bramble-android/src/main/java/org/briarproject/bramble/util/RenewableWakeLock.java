package org.briarproject.bramble.util;

import android.os.PowerManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;

@ThreadSafe
@NotNullByDefault
public class RenewableWakeLock {

	private static final Logger LOG =
			Logger.getLogger(RenewableWakeLock.class.getName());

	/**
	 * Automatically release the lock this many milliseconds after it's due
	 * to have been replaced and released.
	 */
	private static final int SAFETY_MARGIN_MS = 10_000;

	private final PowerManager powerManager;
	private final ScheduledExecutorService scheduler;
	private final int levelAndFlags;
	private final String tag;
	private final long durationMs;
	private final Runnable renewTask;

	private final Object lock = new Object();
	@Nullable
	private PowerManager.WakeLock wakeLock; // Locking: lock
	@Nullable
	private ScheduledFuture future; // Locking: lock

	public RenewableWakeLock(PowerManager powerManager,
			ScheduledExecutorService scheduler, int levelAndFlags, String tag,
			long duration, TimeUnit timeUnit) {
		this.powerManager = powerManager;
		this.scheduler = scheduler;
		this.levelAndFlags = levelAndFlags;
		this.tag = tag;
		durationMs = MILLISECONDS.convert(duration, timeUnit);
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
			wakeLock.acquire(durationMs + SAFETY_MARGIN_MS);
			future = scheduler.schedule(renewTask, durationMs, MILLISECONDS);
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
			wakeLock.acquire(durationMs + SAFETY_MARGIN_MS);
			oldWakeLock.release();
			future = scheduler.schedule(renewTask, durationMs, MILLISECONDS);
		}
	}

	public void release() {
		if (LOG.isLoggable(INFO)) LOG.info("Releasing wake lock " + tag);
		synchronized (lock) {
			if (wakeLock == null) {
				LOG.info("Already released");
				return;
			}
			if (future == null) throw new AssertionError();
			future.cancel(false);
			future = null;
			wakeLock.release();
			wakeLock = null;
		}
	}
}

