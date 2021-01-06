package org.briarproject.briar.android.threaded;

import android.app.Activity;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
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
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadListController.ThreadListListener;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.NamedGroup;
import org.briarproject.briar.api.client.PostHeader;
import org.briarproject.briar.api.client.ThreadedMessage;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.CallSuper;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListControllerImpl<G extends NamedGroup, I extends ThreadItem, H extends PostHeader, M extends ThreadedMessage, L extends ThreadListListener<I>>
		extends DbControllerImpl
		implements ThreadListController<G, I>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	private final EventBus eventBus;
	private final MessageTracker messageTracker;
	private final Map<MessageId, String> textCache = new ConcurrentHashMap<>();

	private volatile GroupId groupId;

	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;

	// UI thread
	protected L listener;

	protected ThreadListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor, EventBus eventBus,
			Clock clock, AndroidNotificationManager notificationManager,
			MessageTracker messageTracker) {
		super(dbExecutor, lifecycleManager);
		this.identityManager = identityManager;
		this.cryptoExecutor = cryptoExecutor;
		this.notificationManager = notificationManager;
		this.clock = clock;
		this.eventBus = eventBus;
		this.messageTracker = messageTracker;
	}

	@Override
	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
	}

	@CallSuper
	@SuppressWarnings("unchecked")
	@Override
	public void onActivityCreate(Activity activity) {
		listener = (L) activity;
	}

	@CallSuper
	@Override
	public void onActivityStart() {
		notificationManager.blockNotification(getGroupId());
		eventBus.addListener(this);
	}

	@CallSuper
	@Override
	public void onActivityStop() {
		notificationManager.unblockNotification(getGroupId());
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {
		MessageId messageId = listener.getFirstVisibleMessageId();
		if (messageId != null) {
			dbExecutor.execute(() -> {
				try {
					messageTracker.storeMessageId(groupId, messageId);
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			});
		}
	}

	@CallSuper
	@Override
	public void eventOccurred(Event e) {
		if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(getGroupId())) {
				LOG.info("Group removed");
				listener.onGroupRemoved();
			}
		}
	}

	@Override
	public void loadItems(
			ResultExceptionHandler<ThreadItemList<I>, DbException> handler) {
		checkGroupId();
		runOnDbThread(() -> {
			try {
				// Load headers
				long start = now();
				Collection<H> headers = loadHeaders();
				logDuration(LOG, "Loading headers", start);

				// Load bodies into cache
				start = now();
				for (H header : headers) {
					if (!textCache.containsKey(header.getId())) {
						textCache.put(header.getId(),
								loadMessageText(header));
					}
				}
				logDuration(LOG, "Loading bodies", start);

				// Build and hand over items
				handler.onResult(buildItems(headers));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@DatabaseExecutor
	protected abstract Collection<H> loadHeaders() throws DbException;

	@DatabaseExecutor
	protected abstract String loadMessageText(H header) throws DbException;

	@Override
	public void markItemRead(I item) {
		markItemsRead(Collections.singletonList(item));
	}

	@Override
	public void markItemsRead(Collection<I> items) {
		runOnDbThread(() -> {
			try {
				long start = now();
				for (I i : items) {
					markRead(i.getId());
				}
				logDuration(LOG, "Marking read", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@DatabaseExecutor
	protected abstract void markRead(MessageId id) throws DbException;

	protected void storePost(M msg, String text,
			ResultExceptionHandler<I, DbException> resultHandler) {
		runOnDbThread(() -> {
			try {
				long start = now();
				H header = addLocalMessage(msg);
				textCache.put(msg.getMessage().getId(), text);
				logDuration(LOG, "Storing message", start);
				resultHandler.onResult(buildItem(header, text));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				resultHandler.onException(e);
			}
		});
	}

	@DatabaseExecutor
	protected abstract H addLocalMessage(M message) throws DbException;

	private ThreadItemList<I> buildItems(Collection<H> headers)
			throws DbException {
		ThreadItemList<I> items = new ThreadItemListImpl<>();
		for (H h : headers) {
			items.add(buildItem(h, textCache.get(h.getId())));
		}
		MessageId msgId = messageTracker.loadStoredMessageId(groupId);
		if (LOG.isLoggable(INFO))
			LOG.info("Loaded last top visible message id " + msgId);
		items.setFirstVisibleId(msgId);
		return items;
	}

	protected abstract I buildItem(H header, String text);

	protected GroupId getGroupId() {
		checkGroupId();
		return groupId;
	}

	private void checkGroupId() {
		if (groupId == null) throw new IllegalStateException();
	}

}
