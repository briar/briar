package org.briarproject.briar.android.removabledrive;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

@NotNullByDefault
abstract class TransferDataState {

	/**
	 * There is nothing we can send to the chosen contact.
	 * This only applies to sending data, but not to receiving it.
	 */
	static class NoDataToSend extends TransferDataState {
	}

	/**
	 * We are ready to let the user select a file for sending or receiving data.
	 */
	static class Ready extends TransferDataState {
	}

	/**
	 * A task with state information is available and should be shown in the UI.
	 */
	static class TaskAvailable extends TransferDataState {
		final RemovableDriveTask.State state;
		/**
		 * This is an old task that is still ongoing. The user needs to wait for
		 * it to complete before starting a new task.
		 */
		final boolean isOldTask;

		TaskAvailable(RemovableDriveTask.State state, boolean isOldTask) {
			this.state = state;
			this.isOldTask = isOldTask;
		}
	}

}
