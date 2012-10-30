package net.sf.briar.api.android;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Enables background threads to make Android API calls that must be made from
 * a thread with a message queue.
 */
public interface AndroidExecutor {

	Future<Void> submit(Runnable r);

	<V> Future<V> submit(Callable<V> c);

	void shutdown();
}
