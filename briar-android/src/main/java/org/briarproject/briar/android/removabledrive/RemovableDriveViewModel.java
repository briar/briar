package org.briarproject.briar.android.removabledrive;

import android.app.Application;
import android.net.Uri;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask.State;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.Locale.US;
import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.PROP_URI;

@UiThread
@NotNullByDefault
class RemovableDriveViewModel extends DbViewModel {

	enum Action {SEND, RECEIVE}

	private final RemovableDriveManager manager;

	private final MutableLiveEvent<Action> action = new MutableLiveEvent<>();
	private final MutableLiveData<TransferDataState> state =
			new MutableLiveData<>();
	private final State initialState = new State(0, 0, false, false);
	@Nullable
	private ContactId contactId = null;
	@Nullable
	private RemovableDriveTask task = null;
	@Nullable
	private Consumer<State> taskObserver = null;

	@Inject
	RemovableDriveViewModel(
			Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			RemovableDriveManager removableDriveManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.manager = removableDriveManager;
	}

	@Override
	protected void onCleared() {
		if (task != null) {
			// when we have a task, we must have an observer for it
			Consumer<State> observer = requireNonNull(taskObserver);
			task.removeObserver(observer);
		}
	}

	/**
	 * Set this as soon as it becomes available.
	 */
	void setContactId(ContactId contactId) {
		this.contactId = contactId;
	}

	@UiThread
	void startSendData() {
		action.setEvent(Action.SEND);

		// check if there is already a send/write task
		task = manager.getCurrentWriterTask();
		if (task == null) {
			// check if there's even something to send
			ContactId c = requireNonNull(contactId);
			runOnDbThread(() -> {
				try {
					if (manager.isWriterTaskNeeded(c)) {
						state.postValue(new TransferDataState.Ready());
					} else {
						state.postValue(new TransferDataState.NoDataToSend());
					}
				} catch (DbException e) {
					handleException(e);
				}
			});
		} else {
			// observe old task and start with initial state
			taskObserver = s -> observeTask(s, true);
			taskObserver.accept(initialState);
			task.addObserver(taskObserver);
		}
	}

	@UiThread
	void startReceiveData() {
		action.setEvent(Action.RECEIVE);

		// check if there is already a receive/read task
		task = manager.getCurrentReaderTask();
		if (task == null) {
			state.setValue(new TransferDataState.Ready());
		} else {
			// observe old task and start with initial state
			taskObserver = s -> observeTask(s, true);
			taskObserver.accept(initialState);
			task.addObserver(taskObserver);
		}
	}

	@UiThread
	private void observeTask(RemovableDriveTask.State s, boolean isOldTask) {
		state.setValue(new TransferDataState.TaskAvailable(s, isOldTask));
	}

	String getFileName() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", US);
		return sdf.format(new Date()) + ".zip";
	}

	/**
	 * Call this only when in {@link TransferDataState.Ready}.
	 */
	@UiThread
	void exportData(Uri uri) {
		// starting an action more than once is not supported for simplicity
		if (task != null) throw new IllegalStateException();

		// from now on, we are not re-usable
		taskObserver = s -> observeTask(s, false);
		taskObserver.accept(initialState);

		// start the writer task for this contact and observe it
		TransportProperties p = new TransportProperties();
		p.put(PROP_URI, uri.toString());
		ContactId c = requireNonNull(contactId);
		task = manager.startWriterTask(c, p);
		task.addObserver(taskObserver);
	}

	/**
	 * Call this only when in {@link TransferDataState.Ready}.
	 */
	@UiThread
	void importData(Uri uri) {
		// starting an action more than once is not supported for simplicity
		if (task != null) throw new IllegalStateException();

		// from now on, we are not re-usable
		taskObserver = s -> observeTask(s, false);
		taskObserver.accept(initialState);

		TransportProperties p = new TransportProperties();
		p.put(PROP_URI, uri.toString());
		task = manager.startReaderTask(p);
		task.addObserver(taskObserver);
	}

	LiveEvent<Action> getActionEvent() {
		return action;
	}

	LiveData<TransferDataState> getState() {
		return state;
	}

}
