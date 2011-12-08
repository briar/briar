package net.sf.briar.db;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An executor that limits the number of concurrent database tasks and the
 * number of tasks queued for execution.
 */
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

	private static final Logger LOG =
		Logger.getLogger(DatabaseExecutorImpl.class.getName());

	private final BlockingQueue<Runnable> queue;

	DatabaseExecutorImpl() {
		this(MAX_QUEUED_TASKS, MIN_THREADS, MAX_THREADS);
	}

	DatabaseExecutorImpl(int maxQueued, int minThreads, int maxThreads) {
		queue = new ArrayBlockingQueue<Runnable>(maxQueued);
		new ThreadPoolExecutor(minThreads, maxThreads, 60, TimeUnit.SECONDS,
				queue);
	}

	public void execute(Runnable r) {
		try {
			// Block until there's space in the queue
			queue.put(r);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(Level.INFO))
				LOG.info("Interrupted while queueing task");
			Thread.currentThread().interrupt();
		}
	}
}
