package net.sf.briar.db;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class DatabaseExecutorImpl implements Executor {

	// FIXME: Determine suitable values for these constants empirically

	/**
	 * The maximum number of tasks that can be queued for execution
	 * before attempting to execute another task will block.
	 */
	private static final int MAX_QUEUED_TASKS = 10;

	/** The number of idle threads to keep in the pool. */
	private static final int MIN_THREADS = 1;

	/** The maximum number of concurrent tasks. */
	private static final int MAX_THREADS = 10;

	private final BlockingQueue<Runnable> queue;

	DatabaseExecutorImpl() {
		this(MAX_QUEUED_TASKS, MIN_THREADS, MAX_THREADS);
	}

	DatabaseExecutorImpl(int maxQueuedTasks, int minThreads, int maxThreads) {
		queue = new ArrayBlockingQueue<Runnable>(maxQueuedTasks);
		new ThreadPoolExecutor(minThreads, maxThreads, 60, TimeUnit.SECONDS,
				queue);
	}

	public void execute(Runnable r) {
		try {
			queue.put(r);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
