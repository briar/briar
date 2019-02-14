package org.briarproject.bramble;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;

@NotNullByDefault
public class PriorityExecutor {

	private final Object lock = new Object();
	private final Executor delegate, high, low;
	@GuardedBy("lock")
	private final Queue<Runnable> highQueue = new LinkedList<>();
	@GuardedBy("lock")
	private final Queue<Runnable> lowQueue = new LinkedList<>();

	@GuardedBy("lock")
	private boolean isTaskRunning = false;

	public PriorityExecutor(Executor delegate) {
		this.delegate = delegate;
		high = r -> submit(r, true);
		low = r -> submit(r, false);
	}

	public Executor getHighPriorityExecutor() {
		return high;
	}

	public Executor getLowPriorityExecutor() {
		return low;
	}

	private void submit(Runnable r, boolean isHighPriority) {
		Runnable wrapped = () -> {
			try {
				r.run();
			} finally {
				scheduleNext();
			}
		};
		synchronized (lock) {
			if (!isTaskRunning && highQueue.isEmpty() &&
					(isHighPriority || lowQueue.isEmpty())) {
				isTaskRunning = true;
				delegate.execute(wrapped);
			} else if (isHighPriority) {
				highQueue.add(wrapped);
			} else {
				lowQueue.add(wrapped);
			}
		}
	}

	private void scheduleNext() {
		synchronized (lock) {
			Runnable next = highQueue.poll();
			if (next == null) next = lowQueue.poll();
			if (next == null) isTaskRunning = false;
			else delegate.execute(next);
		}
	}
}
