package org.briarproject.bramble.system;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.test.SettableClock;
import org.briarproject.bramble.test.TimeTravel;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TimeTravelModule {

	public static class EagerSingletons {
		@Inject
		TaskScheduler scheduler;
	}

	private final ScheduledExecutorService scheduledExecutorService;
	private final Clock clock;
	private final TaskScheduler taskScheduler;
	private final TimeTravel timeTravel;

	public TimeTravelModule() {
		this(false);
	}

	public TimeTravelModule(boolean travel) {
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ScheduledThreadPoolExecutor.DiscardPolicy();
		scheduledExecutorService =
				new ScheduledThreadPoolExecutor(1, policy);
		if (travel) {
			// Use a SettableClock and TestTaskScheduler to allow time travel
			AtomicLong time = new AtomicLong(System.currentTimeMillis());
			clock = new SettableClock(time);
			TestTaskScheduler testTaskScheduler = new TestTaskScheduler(clock);
			taskScheduler = testTaskScheduler;
			timeTravel = new TimeTravel() {
				@Override
				public void setCurrentTimeMillis(long now)
						throws InterruptedException {
					time.set(now);
					testTaskScheduler.runTasks();
				}

				@Override
				public void addCurrentTimeMillis(long add)
						throws InterruptedException {
					time.addAndGet(add);
					testTaskScheduler.runTasks();
				}
			};
		} else {
			// Use the default clock and task scheduler
			clock = new SystemClock();
			taskScheduler = new TaskSchedulerImpl(scheduledExecutorService);
			timeTravel = new TimeTravel() {
				@Override
				public void setCurrentTimeMillis(long now) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void addCurrentTimeMillis(long add) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	@Provides
	Clock provideClock() {
		return clock;
	}

	@Provides
	@Singleton
	TaskScheduler provideTaskScheduler(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(scheduledExecutorService);
		return taskScheduler;
	}

	@Provides
	TimeTravel provideTimeTravel() {
		return timeTravel;
	}
}
