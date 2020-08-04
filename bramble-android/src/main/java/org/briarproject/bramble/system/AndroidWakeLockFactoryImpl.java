package org.briarproject.bramble.system;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockFactory;
import org.briarproject.bramble.api.system.TaskScheduler;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.content.Context.POWER_SERVICE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;

@Immutable
@NotNullByDefault
class AndroidWakeLockFactoryImpl implements AndroidWakeLockFactory {

	/**
	 * How often to replace the wake lock.
	 */
	private static final long LOCK_DURATION_MS = MINUTES.toMillis(1);

	/**
	 * Automatically release the lock this many milliseconds after it's due
	 * to have been replaced and released.
	 */
	private static final long SAFETY_MARGIN_MS = SECONDS.toMillis(10);

	private final TaskScheduler scheduler;
	private final PowerManager powerManager;
	private final String tag;

	@Inject
	AndroidWakeLockFactoryImpl(TaskScheduler scheduler, Application app) {
		this.scheduler = scheduler;
		powerManager = (PowerManager)
				requireNonNull(app.getSystemService(POWER_SERVICE));
		tag = getWakeLockTag(app);
	}

	@Override
	public AndroidWakeLock createWakeLock(int levelAndFlags) {
		return new RenewableWakeLock(powerManager, scheduler, levelAndFlags,
				tag, LOCK_DURATION_MS, SAFETY_MARGIN_MS);
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
