package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.fail;

/**
 * A {@link TaskScheduler} for use in tests. The scheduler keeps all scheduled
 * tasks in a queue until {@link #runTasks()} is called.
 */
@NotNullByDefault
class TestTaskScheduler implements TaskScheduler {

	private final Queue<Task> queue = new PriorityBlockingQueue<>();
	private final Clock clock;

	TestTaskScheduler(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Cancellable schedule(Runnable task, Executor executor, long delay,
			TimeUnit unit) {
		AtomicBoolean cancelled = new AtomicBoolean(false);
		return schedule(task, executor, delay, unit, cancelled);
	}

	@Override
	public Cancellable scheduleWithFixedDelay(Runnable task, Executor executor,
			long delay, long interval, TimeUnit unit) {
		AtomicBoolean cancelled = new AtomicBoolean(false);
		return scheduleWithFixedDelay(task, executor, delay, interval, unit,
				cancelled);
	}

	private Cancellable schedule(Runnable task, Executor executor, long delay,
			TimeUnit unit, AtomicBoolean cancelled) {
		long delayMillis = MILLISECONDS.convert(delay, unit);
		long dueMillis = clock.currentTimeMillis() + delayMillis;
		Task t = new Task(task, executor, dueMillis, cancelled);
		queue.add(t);
		return t;
	}

	private Cancellable scheduleWithFixedDelay(Runnable task, Executor executor,
			long delay, long interval, TimeUnit unit, AtomicBoolean cancelled) {
		// All executions of this periodic task share a cancelled flag
		Runnable wrapped = () -> {
			task.run();
			scheduleWithFixedDelay(task, executor, interval, interval, unit,
					cancelled);
		};
		return schedule(wrapped, executor, delay, unit, cancelled);
	}

	/**
	 * Runs any scheduled tasks that are due.
	 */
	void runTasks() throws InterruptedException {
		long now = clock.currentTimeMillis();
		while (true) {
			Task t = queue.peek();
			if (t == null || t.dueMillis > now) return;
			t = queue.poll();
			// Submit the task to its executor and wait for it to finish
			if (!t.run().await(1, MINUTES)) fail();
		}
	}

	private static class Task
			implements Cancellable, Comparable<Task> {

		private final Runnable task;
		private final Executor executor;
		private final long dueMillis;
		private final AtomicBoolean cancelled;

		private Task(Runnable task, Executor executor, long dueMillis,
				AtomicBoolean cancelled) {
			this.task = task;
			this.executor = executor;
			this.dueMillis = dueMillis;
			this.cancelled = cancelled;
		}

		@SuppressWarnings("UseCompareMethod") // Animal Sniffer
		@Override
		public int compareTo(Task task) {
			return Long.valueOf(dueMillis).compareTo(task.dueMillis);
		}

		/**
		 * Submits the task to its executor and returns a latch that will be
		 * released when the task finishes.
		 */
		public CountDownLatch run() {
			if (cancelled.get()) return new CountDownLatch(0);
			CountDownLatch latch = new CountDownLatch(1);
			executor.execute(() -> {
				task.run();
				latch.countDown();
			});
			return latch;
		}

		@Override
		public void cancel() {
			cancelled.set(true);
		}
	}
}
