package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A service that can be used to schedule the execution of tasks.
 */
@NotNullByDefault
public interface TaskScheduler {

	/**
	 * Submits the given task to the given executor after the given delay.
	 */
	Future<?> schedule(Runnable task, Executor executor, long delay,
			TimeUnit unit);

	/**
	 * Submits the given task to the given executor after the given delay,
	 * and then repeatedly with the given interval between executions
	 * (measured from the end of one execution to the beginning of the next).
	 */
	Future<?> scheduleWithFixedDelay(Runnable task, Executor executor,
			long delay, long interval, TimeUnit unit);
}
