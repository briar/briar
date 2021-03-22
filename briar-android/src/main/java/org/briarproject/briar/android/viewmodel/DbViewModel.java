package org.briarproject.briar.android.viewmodel;

import android.app.Application;
import android.widget.Toast;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbCallable;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.DbRunnable;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.arch.core.util.Function;
import androidx.core.util.Consumer;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@Immutable
@NotNullByDefault
public abstract class DbViewModel extends AndroidViewModel {

	private static final Logger LOG = getLogger(DbViewModel.class.getName());

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final LifecycleManager lifecycleManager;
	private final TransactionManager db;
	protected final AndroidExecutor androidExecutor;

	public DbViewModel(
			@NonNull Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor) {
		super(application);
		this.dbExecutor = dbExecutor;
		this.lifecycleManager = lifecycleManager;
		this.db = db;
		this.androidExecutor = androidExecutor;
	}

	/**
	 * Waits for the DB to open and runs the given task on the
	 * {@link DatabaseExecutor}.
	 * <p>
	 * If you need a list of items to be displayed in a
	 * {@link RecyclerView.Adapter},
	 * use {@link #loadList(DbCallable, UiConsumer)} instead.
	 */
	protected void runOnDbThread(Runnable task) {
		dbExecutor.execute(() -> {
			try {
				lifecycleManager.waitForDatabase();
				task.run();
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for database");
				Thread.currentThread().interrupt();
			}
		});
	}

	/**
	 * Waits for the DB to open and runs the given task on the
	 * {@link DatabaseExecutor}.
	 * <p>
	 * If you need a list of items to be displayed in a
	 * {@link RecyclerView.Adapter},
	 * use {@link #loadList(DbCallable, UiConsumer)} instead.
	 */
	protected void runOnDbThread(boolean readOnly,
			DbRunnable<Exception> task, Consumer<Exception> err) {
		dbExecutor.execute(() -> {
			try {
				lifecycleManager.waitForDatabase();
				db.transaction(readOnly, task);
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for database");
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				err.accept(e);
			}
		});
	}

	/**
	 * Loads a list of items on the {@link DatabaseExecutor} within a single
	 * {@link Transaction} and publishes it as a {@link LiveResult}
	 * to the {@link UiThread}.
	 * <p>
	 * Use this to ensure that modifications to your local list do not get
	 * overridden by database loads that were in progress while the modification
	 * was made.
	 * E.g. An event about the removal of a message causes the message item to
	 * be removed from the local list while all messages are reloaded.
	 * This method ensures that those operations can be processed on the
	 * UiThread in the correct order so that the removed message will not be
	 * re-added when the re-load completes.
	 */
	protected <T extends List<?>> void loadList(
			DbCallable<T, DbException> task,
			UiConsumer<LiveResult<T>> uiConsumer) {
		dbExecutor.execute(() -> {
			try {
				lifecycleManager.waitForDatabase();
				db.transaction(true, txn -> {
					T t = task.call(txn);
					txn.attach(() -> uiConsumer.accept(new LiveResult<>(t)));
				});
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for database");
				Thread.currentThread().interrupt();
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				androidExecutor.runOnUiThread(
						() -> uiConsumer.accept(new LiveResult<>(e)));
			}
		});
	}

	@NotNullByDefault
	public interface UiConsumer<T> {
		@UiThread
		void accept(T t);
	}

	/**
	 * Creates a copy of the list available in the given LiveData
	 * and adds the given item to the copy.
	 *
	 * @return a copy of the list in the LiveData with item added or null when
	 * <ul>
	 * <li> LiveData does not have a value
	 * <li> LiveResult in the LiveData has an error
	 * </ul>
	 */
	@Nullable
	protected <T> List<T> addListItem(LiveData<LiveResult<List<T>>> liveData,
			T item) {
		List<T> items = getListCopy(liveData);
		if (items == null) return null;
		items.add(item);
		return items;
	}

	/**
	 * Creates a copy of the list available in the given LiveData
	 * and adds the given items to the copy.
	 *
	 * @return a copy of the list in the LiveData with items added or null when
	 * <ul>
	 * <li> LiveData does not have a value
	 * <li> LiveResult in the LiveData has an error
	 * </ul>
	 */
	@Nullable
	protected <T> List<T> addListItems(LiveData<LiveResult<List<T>>> liveData,
			Collection<T> items) {
		List<T> copiedItems = getListCopy(liveData);
		if (copiedItems == null) return null;
		copiedItems.addAll(items);
		return copiedItems;
	}

	/**
	 * Creates a copy of the list available in the given LiveData
	 * and replaces items where the given test function returns true.
	 *
	 * @return a copy of the list in the LiveData with item(s) replaced
	 * or null when the
	 * <ul>
	 * <li> LiveData does not have a value
	 * <li> LiveResult in the LiveData has an error
	 * <li> test function did return false for all items in the list
	 * </ul>
	 */
	@Nullable
	protected <T> List<T> updateListItems(
			LiveData<LiveResult<List<T>>> liveData, Function<T, Boolean> test,
			Function<T, T> replacer) {
		List<T> items = getListCopy(liveData);
		if (items == null) return null;

		ListIterator<T> iterator = items.listIterator();
		boolean changed = false;
		while (iterator.hasNext()) {
			T item = iterator.next();
			if (test.apply(item)) {
				changed = true;
				iterator.set(replacer.apply(item));
			}
		}
		return changed ? items : null;
	}

	/**
	 * Creates a copy of the list available in the given LiveData
	 * and removes the items from it where the given test function returns true.
	 *
	 * @return a copy of the list in the LiveData with item(s) removed
	 * or null when the
	 * <ul>
	 * <li> LiveData does not have a value
	 * <li> LiveResult in the LiveData has an error
	 * <li> test function did return false for all items in the list
	 * </ul>
	 */
	@Nullable
	protected <T> List<T> removeListItems(
			LiveData<LiveResult<List<T>>> liveData, Function<T, Boolean> test) {
		List<T> items = getListCopy(liveData);
		if (items == null) return null;

		ListIterator<T> iterator = items.listIterator();
		boolean changed = false;
		while (iterator.hasNext()) {
			T item = iterator.next();
			if (test.apply(item)) {
				changed = true;
				iterator.remove();
			}
		}
		return changed ? items : null;
	}

	/**
	 * Updates the given LiveData with a copy of its list
	 * with the items removed where the given test function returns true.
	 * <p>
	 * Nothing is updated, if the
	 * <ul>
	 * <li> LiveData does not have a value
	 * <li> LiveResult in the LiveData has an error
	 * <li> test function did return false for all items in the list
	 * </ul>
	 */
	@UiThread
	protected <T> void removeAndUpdateListItems(
			MutableLiveData<LiveResult<List<T>>> liveData,
			Function<T, Boolean> test) {
		List<T> list = removeListItems(liveData, test);
		if (list != null) liveData.setValue(new LiveResult<>(list));
	}

	/**
	 * Retrieves a copy of the list of items from the given LiveData
	 * or null if it is not available.
	 * The list copy can be safely mutated.
	 */
	@Nullable
	private <T> List<T> getListCopy(LiveData<LiveResult<List<T>>> liveData) {
		LiveResult<List<T>> value = liveData.getValue();
		if (value == null) return null;
		List<T> list = value.getResultOrNull();
		if (list == null) return null;
		return new ArrayList<>(list);
	}

	/**
	 * Logs the exception and shows a Toast to the user.
	 * <p>
	 * Errors that are likely or expected to happen should not use this method
	 * and show proper error states in UI.
	 */
	@AnyThread
	protected void handleException(Exception e) {
		logException(LOG, WARNING, e);
		androidExecutor.runOnUiThread(() -> {
			String msg = "Error: " + e.getClass().getSimpleName();
			if (!StringUtils.isNullOrEmpty(e.getMessage())) {
				msg += " " + e.getMessage();
			}
			if (e.getCause() != null) {
				msg += " caused by " + e.getCause().getClass().getSimpleName();
			}
			Toast.makeText(getApplication(), msg, LENGTH_LONG).show();
		});
	}

}
