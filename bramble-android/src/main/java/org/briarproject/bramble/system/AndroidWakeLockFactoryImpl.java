package org.briarproject.bramble.system;

import android.app.Application;
import android.os.PowerManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockFactory;
import org.briarproject.bramble.api.system.TaskScheduler;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.content.Context.POWER_SERVICE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.util.AndroidUtils.getWakeLockTag;

@Immutable
@NotNullByDefault
class AndroidWakeLockFactoryImpl implements AndroidWakeLockFactory {

	private static final long LOCK_DURATION_MS = MINUTES.toMillis(1);

	private final TaskScheduler scheduler;
	private final Application app;
	private final PowerManager powerManager;

	@Inject
	AndroidWakeLockFactoryImpl(TaskScheduler scheduler, Application app) {
		this.scheduler = scheduler;
		this.app = app;
		powerManager = (PowerManager)
				requireNonNull(app.getSystemService(POWER_SERVICE));
	}

	@Override
	public AndroidWakeLock createWakeLock(int levelAndFlags) {
		String tag = getWakeLockTag(app);
		return new RenewableWakeLock(powerManager, scheduler, levelAndFlags,
				tag, LOCK_DURATION_MS, MILLISECONDS);
	}
}
