package org.briarproject.bramble.system;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;

@ThreadSafe
@NotNullByDefault
class RenewableWakeLock implements SharedWakeLock {

	private static final Logger LOG =
			getLogger(RenewableWakeLock.class.getName());

	private final PowerManager powerManager;
	private final TaskScheduler scheduler;
	private final int levelAndFlags;
	private final String tag;
	private final long durationMs, safetyMarginMs;

	private final Object lock = new Object();
	@GuardedBy("lock")
	@Nullable
	private WakeLock wakeLock;
	@GuardedBy("lock")
	@Nullable
	private Future<?> future;
	@GuardedBy("lock")
	private int refCount = 0;
	@GuardedBy("lock")
	private long acquired = 0;

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
		synchronized (lock) {
			refCount++;
			if (refCount == 1) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Acquiring wake lock " + tag);
				}
				wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
				// We do our own reference counting so we can replace the lock
				// TODO: Check whether using a ref-counted wake lock affects
				//  power management apps
				wakeLock.setReferenceCounted(false);
				wakeLock.acquire(durationMs + safetyMarginMs);
				future = scheduler.schedule(this::renew, durationMs,
						MILLISECONDS);
				acquired = android.os.SystemClock.elapsedRealtime();
			}
		}
	}

	private void renew() {
		if (LOG.isLoggable(INFO)) LOG.info("Renewing wake lock " + tag);
		synchronized (lock) {
			if (wakeLock == null) {
				LOG.info("Already released");
				return;
			}
			long now = android.os.SystemClock.elapsedRealtime();
			long expiry = acquired + durationMs + safetyMarginMs;
			if (now > expiry && LOG.isLoggable(WARNING)) {
				LOG.warning("Wake lock expired " + (now - expiry) + " ms ago");
			}
			WakeLock oldWakeLock = wakeLock;
			wakeLock = powerManager.newWakeLock(levelAndFlags, tag);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire(durationMs + safetyMarginMs);
			oldWakeLock.release();
			future = scheduler.schedule(this::renew, durationMs, MILLISECONDS);
			acquired = now;
		}
	}

	@Override
	public void release() {
		synchronized (lock) {
			refCount--;
			if (refCount == 0) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Releasing wake lock " + tag);
				}
				requireNonNull(future).cancel(false);
				future = null;
				requireNonNull(wakeLock).release();
				wakeLock = null;
				acquired = 0;
			}
		}
	}
}

