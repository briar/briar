package org.briarproject.bramble.api.plugin.file;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public interface RemovableDriveTask extends Runnable {

	/**
	 * Returns the file that this task is reading from or writing to.
	 */
	File getFile();

	/**
	 * Adds an observer to the task. The observer will be notified of state
	 * changes on the event thread. If the task has already finished, the
	 * observer will be notified of its final state.
	 */
	void addObserver(Consumer<State> observer);

	/**
	 * Removes an observer from the task.
	 */
	void removeObserver(Consumer<State> observer);

	class State {

		private final long done, total;
		private final boolean finished, success;

		public State(long done, long total, boolean finished, boolean success) {
			this.done = done;
			this.total = total;
			this.finished = finished;
			this.success = success;
		}

		public long getDone() {
			return done;
		}

		public long getTotal() {
			return total;
		}

		public boolean isFinished() {
			return finished;
		}

		public boolean isSuccess() {
			return success;
		}
	}
}
