package org.briarproject.bramble.api.plugin.file;

import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public interface RemovableDriveTask extends Runnable {

	/**
	 * Returns the file that this task is reading from or writing to.
	 */
	File getFile();

	/**
	 * Adds an observer to the task.
	 */
	void addObserver(Observer o);

	/**
	 * Removes an observer from the task.
	 */
	void removeObserver(Observer o);

	interface Observer {

		@EventExecutor
		void onProgress(long written, long total);

		@EventExecutor
		void onCompletion(boolean success);
	}
}
