package org.briarproject.bramble.api.system;

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
	<V> Future<V> runOnBackgroundThread(Callable<V> c);

	/**
	 * Runs the given task on a background thread with a message queue.
	 */
	void runOnBackgroundThread(Runnable r);

	/**
	 * Runs the given task on the main UI thread and returns a Future for
	 * getting the result.
	 */
	<V> Future<V> runOnUiThread(Callable<V> c);

	/**
	 * Runs the given task on the main UI thread.
	 */
	void runOnUiThread(Runnable r);
}
