package org.briarproject.android.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Enables background threads to make Android API calls that must be made from
 * a thread with a message queue.
 */
public interface AndroidExecutor {

	/**
	 * Runs the given task on a background thread with a message queue and
	 * returns a Future for getting the result.
	 */
	<V> Future<V> submit(Callable<V> c);

	/**
	 * Runs the given task on a background thread with a message queue.
	 */
	void execute(Runnable r);
}
