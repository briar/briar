package net.sf.briar.util;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * An executor that limits the number of concurrently executing tasks and the
 * number of tasks queued for execution.
 */
public class BoundedExecutor implements Executor {

	private static final Logger LOG =
		Logger.getLogger(BoundedExecutor.class.getName());

	private final Semaphore semaphore;
	private final BlockingQueue<Runnable> queue;
	private final Executor executor;

	public BoundedExecutor(int maxQueued, int minThreads, int maxThreads) {
		semaphore = new Semaphore(maxQueued + maxThreads);
		queue = new LinkedBlockingQueue<Runnable>();
		executor = new ThreadPoolExecutor(minThreads, maxThreads, 60, SECONDS,
				queue);
	}

	public void execute(final Runnable r) {
		try {
			semaphore.acquire();
			executor.execute(new Runnable() {
				public void run() {
					try {
						r.run();
					} finally {
						semaphore.release();
					}
				}
			});
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while queueing task");
			Thread.currentThread().interrupt();
			throw new RejectedExecutionException();
		} catch(RejectedExecutionException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			semaphore.release();
			throw e;
		}
	}
}
