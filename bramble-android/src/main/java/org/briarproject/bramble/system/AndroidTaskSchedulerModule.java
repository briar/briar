package org.briarproject.bramble.system;

import android.app.Application;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.AlarmListener;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidTaskSchedulerModule {

	public static class EagerSingletons {
		@Inject
		AndroidTaskScheduler scheduler;
	}

	@Provides
	@Singleton
	AndroidTaskScheduler provideAndroidTaskScheduler(
			LifecycleManager lifecycleManager, Application app,
			AndroidWakeLockManager wakeLockManager,
			ScheduledExecutorService scheduledExecutorService) {
		AndroidTaskScheduler scheduler = new AndroidTaskScheduler(app,
				wakeLockManager, scheduledExecutorService);
		lifecycleManager.registerService(scheduler);
		return scheduler;
	}

	@Provides
	@Singleton
	AlarmListener provideAlarmListener(AndroidTaskScheduler scheduler) {
		return scheduler;
	}

	@Provides
	@Singleton
	TaskScheduler provideTaskScheduler(AndroidTaskScheduler scheduler) {
		return scheduler;
	}
}
