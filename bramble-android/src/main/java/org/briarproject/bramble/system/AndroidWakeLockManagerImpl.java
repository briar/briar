package org.briarproject.bramble.system;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;

import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;

@Immutable
@NotNullByDefault
class AndroidWakeLockManagerImpl implements AndroidWakeLockManager {

	/**
	 * How often to replace the wake lock.
	 */
	private static final long LOCK_DURATION_MS = MINUTES.toMillis(1);

	/**
	 * Automatically release the lock this many milliseconds after it's due
	 * to have been replaced and released.
	 */
	private static final long SAFETY_MARGIN_MS = SECONDS.toMillis(30);

	private final SharedWakeLock sharedWakeLock;

	@Inject
	AndroidWakeLockManagerImpl(TaskScheduler scheduler,
			@EventExecutor Executor eventExecutor,
			Application app) {
		PowerManager powerManager = (PowerManager)
				requireNonNull(app.getSystemService(POWER_SERVICE));
		String tag = getWakeLockTag(app);
		sharedWakeLock = new RenewableWakeLock(powerManager, scheduler,
				eventExecutor, PARTIAL_WAKE_LOCK, tag, LOCK_DURATION_MS,
				SAFETY_MARGIN_MS);
	}

	@Override
	public AndroidWakeLock createWakeLock() {
		return new AndroidWakeLockImpl(sharedWakeLock);
	}

	@Override
	public void runWakefully(Runnable r) {
		AndroidWakeLock wakeLock = createWakeLock();
		wakeLock.acquire();
		try {
			r.run();
		} finally {
			wakeLock.release();
		}
	}

	@Override
	public void executeWakefully(Runnable r, Executor executor) {
		AndroidWakeLock wakeLock = createWakeLock();
		wakeLock.acquire();
		try {
			executor.execute(() -> {
				try {
					r.run();
				} finally {
					wakeLock.release();
				}
			});
		} catch (Exception e) {
			wakeLock.release();
			throw e;
		}
	}

	private String getWakeLockTag(Context ctx) {
		PackageManager pm = ctx.getPackageManager();
		for (PackageInfo info : pm.getInstalledPackages(0)) {
			String name = info.packageName.toLowerCase();
			if (name.startsWith("com.huawei.powergenie")) {
				return "LocationManagerService";
			} else if (name.startsWith("com.evenwell.powermonitor")) {
				return "AudioIn";
			}
		}
		return ctx.getPackageName();
	}
}
