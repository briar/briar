package org.briarproject.system;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.system.Clock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SystemModule {

	public static class EagerSingletons {
		@Inject
		ScheduledExecutorService scheduledExecutorService;
	}

	private final ScheduledExecutorService scheduler;

	public SystemModule() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
	}

	@Provides
	Clock provideClock() {
		return new SystemClock();
	}

	@Provides
	@Singleton
	ScheduledExecutorService provideScheduledExecutorService(
			LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(scheduler);
		return scheduler;
	}
}
