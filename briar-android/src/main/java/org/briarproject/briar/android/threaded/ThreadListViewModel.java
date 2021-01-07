package org.briarproject.briar.android.threaded;

import android.app.Application;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.PostHeader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListViewModel<I extends ThreadItem> extends DbViewModel
		implements EventListener {

	private static final Logger LOG =
			getLogger(ThreadListViewModel.class.getName());

	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;
	private final MessageTracker messageTracker;
	private final EventBus eventBus;

	private final Map<MessageId, String> textCache = new ConcurrentHashMap<>();
	private final MutableLiveData<LiveResult<List<I>>> items =
			new MutableLiveData<>();

	protected volatile GroupId groupId;

	public ThreadListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			IdentityManager identityManager,
			AndroidNotificationManager notificationManager,
			@CryptoExecutor Executor cryptoExecutor,
			Clock clock,
			MessageTracker messageTracker,
			EventBus eventBus) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.identityManager = identityManager;
		this.notificationManager = notificationManager;
		this.cryptoExecutor = cryptoExecutor;
		this.clock = clock;
		this.messageTracker = messageTracker;
		this.eventBus = eventBus;
		this.eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	/**
	 * Needs to be called right after initialization,
	 * before calling other methods.
	 */
	@CallSuper
	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
		loadItems();
	}

	public abstract void loadItems();

	@UiThread
	protected void setItems(LiveResult<List<I>> items) {
		this.items.setValue(items);
	}

	@DatabaseExecutor
	protected <H extends PostHeader> List<I> buildItems(
			Transaction txn, Collection<H> headers, ItemGetter<H, I> itemGetter)
			throws DbException {
		long start = now();
		ThreadItemList<I> items = new ThreadItemListImpl<>();
		for (H header : headers) {
			MessageId id = header.getId();
			String text = textCache.get(header.getId());
			if (text == null) {
				text = loadMessageText(txn, header);
				textCache.put(id, text);
			}
			items.add(itemGetter.getItem(header, text));
		}
		logDuration(LOG, "Loading bodies and creating items", start);

		MessageId msgId = messageTracker.loadStoredMessageId(txn, groupId);
		if (LOG.isLoggable(INFO)) {
			LOG.info("Loaded last top visible message id " + msgId);
		}
		// TODO store this elsewhere
		items.setFirstVisibleId(msgId);
		return items;
	}

	@DatabaseExecutor
	protected abstract String loadMessageText(Transaction txn,
			PostHeader header) throws DbException;

	LiveData<LiveResult<List<I>>> getItems() {
		return items;
	}

	public interface ItemGetter<H extends PostHeader, I> {
		I getItem(H header, String text);
	}

}
