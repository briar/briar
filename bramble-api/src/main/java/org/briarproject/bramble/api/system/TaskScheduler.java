package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A service that can be used to schedule the execution of tasks.
 * <p>
 * The service should only be used for running tasks on other executors
 * at scheduled times. No significant work should be run by the service itself.
 */
@NotNullByDefault
public interface TaskScheduler {

	/**
	 * See {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}.
	 */
	Future<?> schedule(Runnable task, long delay, TimeUnit unit);

	/**
	 * See {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)}.
	 */
	Future<?> scheduleWithFixedDelay(Runnable task, long delay,
			long interval, TimeUnit unit);
}
