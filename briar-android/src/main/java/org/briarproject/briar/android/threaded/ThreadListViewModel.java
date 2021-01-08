package org.briarproject.briar.android.threaded;

import android.app.Application;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTree;
import org.briarproject.briar.api.client.PostHeader;
import org.briarproject.briar.client.MessageTreeImpl;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListViewModel<I extends ThreadItem>
		extends DbViewModel
		implements EventListener {

	private static final Logger LOG =
			getLogger(ThreadListViewModel.class.getName());

	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;
	private final MessageTracker messageTracker;
	private final EventBus eventBus;

	@DatabaseExecutor
	private final MessageTree<I> messageTree = new MessageTreeImpl<>();
	private final MutableLiveData<LiveResult<List<I>>> items =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> groupRemoved =
			new MutableLiveData<>();
	private final AtomicReference<MessageId> scrollToItem =
			new AtomicReference<>();

	protected volatile GroupId groupId;
	@Nullable
	private MessageId replyId;
	private final AtomicReference<MessageId> storedMessageId =
			new AtomicReference<>();

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
		loadStoredMessageId();
		loadItems();
	}

	public void blockNotifications() {
		notificationManager.blockNotification(groupId);
	}

	public void unblockNotifications() {
		notificationManager.unblockNotification(groupId);
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(groupId)) {
				LOG.info("Group removed");
				groupRemoved.setValue(true);
			}
		}
	}

	private void loadStoredMessageId() {
		runOnDbThread(() -> {
			try {
				storedMessageId
						.set(messageTracker.loadStoredMessageId(groupId));
				if (LOG.isLoggable(INFO)) {
					LOG.info("Loaded last top visible message id " +
							storedMessageId);
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	public abstract void loadItems();

	public abstract void createAndStoreMessage(String text,
			@Nullable I parentItem);

	@UiThread
	protected void setItems(LiveResult<List<I>> items) {
		this.items.setValue(items);
	}

	@DatabaseExecutor
	protected <H extends PostHeader> List<I> createItems(
			Transaction txn, Collection<H> headers, ItemGetter<H, I> itemGetter)
			throws DbException {
		long start = now();
		ThreadItemList<I> items = new ThreadItemListImpl<>();
		for (H header : headers) {
			String text = loadMessageText(txn, header);
			items.add(itemGetter.getItem(header, text));
		}
		logDuration(LOG, "Loading bodies and creating items", start);

		messageTree.clear();
		messageTree.add(items);
		return messageTree.depthFirstOrder();
	}

	/**
	 * Add a remote item on the UI thread.
	 * The list will not scroll, but show an unread indicator.
	 */
	@UiThread
	protected void addItem(I item) {
		messageTree.add(item);
		items.setValue(new LiveResult<>(messageTree.depthFirstOrder()));
	}

	/**
	 * Add a local item from the DB thread.
	 * The list will scroll to the new item.
	 */
	@DatabaseExecutor
	protected void addItemAsync(I item) {
		messageTree.add(item);
		scrollToItem.set(item.getId());
		items.postValue(new LiveResult<>(messageTree.depthFirstOrder()));
	}

	@DatabaseExecutor
	protected abstract String loadMessageText(Transaction txn,
			PostHeader header) throws DbException;

	@UiThread
	public void setReplyId(@Nullable MessageId id) {
		replyId = id;
	}

	@UiThread
	@Nullable
	public MessageId getReplyId() {
		return replyId;
	}

	void storeMessageId(@Nullable MessageId messageId) {
		if (messageId != null) runOnDbThread(() -> {
			try {
				messageTracker.storeMessageId(groupId, messageId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	protected abstract void markItemRead(I item);

	@Nullable
	MessageId getAndResetRestoredMessageId() {
		return storedMessageId.getAndSet(null);
	}

	LiveData<LiveResult<List<I>>> getItems() {
		return items;
	}

	LiveData<Boolean> getGroupRemoved() {
		return groupRemoved;
	}

	@Nullable
	MessageId getAndResetScrollToItem() {
		return scrollToItem.getAndSet(null);
	}

	public interface ItemGetter<H extends PostHeader, I> {
		I getItem(H header, String text);
	}

}
