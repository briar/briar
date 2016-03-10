package org.briarproject.api.android;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Enables background threads to make Android API calls that must be made from
 * a thread with a message queue.
 */
public interface PlatformExecutor {

	/**
	 * Runs the given task on the main UI thread and returns a Future for
	 * getting the result.
	 */
	<V> Future<V> submit(Callable<V> c);

	/** Runs the given task on the main UI thread. */
	void execute(Runnable r);
}
