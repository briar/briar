package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link TaskScheduler} that uses a {@link ScheduledExecutorService}.
 */
@ThreadSafe
@NotNullByDefault
class TaskSchedulerImpl implements TaskScheduler {

	private final ScheduledExecutorService scheduledExecutorService;

	TaskSchedulerImpl(ScheduledExecutorService scheduledExecutorService) {
		this.scheduledExecutorService = scheduledExecutorService;
	}

	@Override
	public Cancellable schedule(Runnable task, Executor executor, long delay,
			TimeUnit unit) {
		Runnable execute = () -> executor.execute(task);
		ScheduledFuture<?> future =
				scheduledExecutorService.schedule(execute, delay, unit);
		return () -> future.cancel(false);
	}

	@Override
	public Cancellable scheduleWithFixedDelay(Runnable task, Executor executor,
			long delay, long interval, TimeUnit unit) {
		Runnable execute = () -> executor.execute(task);
		ScheduledFuture<?> future = scheduledExecutorService.
				scheduleWithFixedDelay(execute, delay, interval, unit);
		return () -> future.cancel(false);
	}
}
