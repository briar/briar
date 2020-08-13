package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

@NotNullByDefault
public interface AndroidWakeLockManager {

	/**
	 * Creates a wake lock with the given tag. The tag is only used for
	 * logging; the underlying OS wake lock will use its own tag.
	 */
	AndroidWakeLock createWakeLock(String tag);

	/**
	 * Runs the given task while holding a wake lock.
	 */
	void runWakefully(Runnable r, String tag);

	/**
	 * Submits the given task to the given executor while holding a wake lock.
	 * The lock is released when the task completes, or if an exception is
	 * thrown while submitting or running the task.
	 */
	void executeWakefully(Runnable r, Executor executor, String tag);

	/**
	 * Starts a dedicated thread to run the given task asynchronously. A wake
	 * lock is acquired before starting the thread and released when the task
	 * completes, or if an exception is thrown while starting the thread or
	 * running the task.
	 * <p>
	 * This method should only be used for lifecycle management tasks that
	 * can't be run on an executor.
	 */
	void executeWakefully(Runnable r, String tag);
}
