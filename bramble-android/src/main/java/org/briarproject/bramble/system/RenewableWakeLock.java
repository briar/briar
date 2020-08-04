package org.briarproject.bramble.system;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
class RenewableWakeLock implements AndroidWakeLock {

	private static final Logger LOG =
			getLogger(RenewableWakeLock.class.getName());

	private final PowerManager powerManager;
	private final TaskScheduler scheduler;
	private final int levelAndFlags;
	private final String tag;
	private final long durationMs, safetyMarginMs;

	private final Object lock = new Object();
	@Nullable
	private WakeLock wakeLock; // Locking: lock
	@Nullable
	private Future<?> future; // Locking: lock

	RenewableWakeLock(PowerManager powerManager, TaskScheduler scheduler,
			int levelAndFlags, String tag, long durationMs,
			long safetyMarginMs) {
		this.powerManager = powerManager;
		this.scheduler = scheduler;
		this.levelAndFlags = levelAndFlags;
		this.tag = tag;
		this.durationMs = durationMs;
		this.safetyMarginMs = safetyMarginMs;
	}

	@Override
	public void acquire() {
		if (LOG.isLoggable(INFO)) LOG.info("Acquiring wake lock " + tag);
		synchronized (lock) {
			if (wakeLock != null) {
				LOG.info("Already acquired");
				return;
			}
			wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire(durationMs + safetyMarginMs);
			future = scheduler.schedule(this::renew, durationMs, MILLISECONDS);
		}
	}

	private void renew() {
		if (LOG.isLoggable(INFO)) LOG.info("Renewing wake lock " + tag);
		synchronized (lock) {
			if (wakeLock == null) {
				LOG.info("Already released");
				return;
			}
			WakeLock oldWakeLock = wakeLock;
			wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire(durationMs + safetyMarginMs);
			oldWakeLock.release();
			future = scheduler.schedule(this::renew, durationMs, MILLISECONDS);
		}
	}

	@Override
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

