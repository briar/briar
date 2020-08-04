package org.briarproject.bramble.system;

import android.app.Application;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.AlarmListener;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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

	private final ScheduledExecutorService scheduledExecutorService;

	public AndroidTaskSchedulerModule() {
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ScheduledThreadPoolExecutor.DiscardPolicy();
		scheduledExecutorService = new ScheduledThreadPoolExecutor(1, policy);
	}

	@Provides
	@Singleton
	AndroidTaskScheduler provideAndroidTaskScheduler(
			LifecycleManager lifecycleManager, Application app) {
		lifecycleManager.registerForShutdown(scheduledExecutorService);
		AndroidTaskScheduler scheduler =
				new AndroidTaskScheduler(app, scheduledExecutorService);
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
