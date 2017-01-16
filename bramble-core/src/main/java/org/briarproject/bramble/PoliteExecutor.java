package org.briarproject.bramble;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;

import static java.util.logging.Level.FINE;

/**
 * An {@link Executor} that delegates its tasks to another {@link Executor}
 * while limiting the number of tasks that are delegated concurrently. Tasks
 * are delegated in the order they are submitted to this executor.
 */
@NotNullByDefault
public class PoliteExecutor implements Executor {

	private static final Level LOG_LEVEL = FINE;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Queue<Runnable> queue = new LinkedList<Runnable>();
	private final Executor delegate;
	private final int maxConcurrentTasks;
	private final Logger log;

	@GuardedBy("lock")
	private int concurrentTasks = 0;

	/**
	 * @param tag the tag to be used for logging
	 * @param delegate the executor to which tasks will be delegated
	 * @param maxConcurrentTasks the maximum number of tasks that will be
	 * delegated concurrently. If this is set to 1, tasks submitted to this
	 * executor will run in the order they are submitted and will not run
	 * concurrently
	 */
	public PoliteExecutor(String tag, Executor delegate,
			int maxConcurrentTasks) {
		this.delegate = delegate;
		this.maxConcurrentTasks = maxConcurrentTasks;
		log = Logger.getLogger(tag);
	}

	@Override
	public void execute(final Runnable r) {
		final long submitted = System.currentTimeMillis();
		Runnable wrapped = new Runnable() {
			@Override
			public void run() {
				if (log.isLoggable(LOG_LEVEL)) {
					long queued = System.currentTimeMillis() - submitted;
					log.log(LOG_LEVEL, "Queue time " + queued + " ms");
				}
				try {
					r.run();
				} finally {
					scheduleNext();
				}
			}
		};
		synchronized (lock) {
			if (concurrentTasks < maxConcurrentTasks) {
				concurrentTasks++;
				delegate.execute(wrapped);
			} else {
				queue.add(wrapped);
			}
		}
	}

	private void scheduleNext() {
		synchronized (lock) {
			Runnable next = queue.poll();
			if (next == null) concurrentTasks--;
			else delegate.execute(next);
		}
	}
}
