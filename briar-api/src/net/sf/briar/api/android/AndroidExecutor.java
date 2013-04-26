package net.sf.briar.api.android;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Enables background threads to make Android API calls that must be made from
 * a thread with a message queue.
 */
public interface AndroidExecutor {

	/**
	 * Runs the given task on a thread with a message queue and returns the
	 * result of the task.
	 */
	<V> V call(Callable<V> c) throws InterruptedException, ExecutionException;

	void shutdown();
}
