package org.briarproject.briar.android.threaded;

import android.app.Activity;
import android.support.annotation.CallSuper;

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
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
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

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListControllerImpl<G extends NamedGroup, I extends ThreadItem, H extends PostHeader, M extends ThreadedMessage, L extends ThreadListListener<H>>
		extends DbControllerImpl
		implements ThreadListController<G, I, H>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	private final EventBus eventBus;
	private final Map<MessageId, String> bodyCache = new ConcurrentHashMap<>();
	private volatile GroupId groupId;

	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;
	private final MessageTracker messageTracker;
	protected volatile L listener;

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
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					messageTracker
							.storeMessageId(groupId,
									listener.getFirstVisibleMessageId());
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@CallSuper
	@Override
	public void eventOccurred(Event e) {
		if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(getGroupId())) {
				LOG.info("Group removed");
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onGroupRemoved();
					}
				});
			}
		}
	}

	@Override
	public void loadNamedGroup(
			final ResultExceptionHandler<G, DbException> handler) {
		checkGroupId();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					G groupItem = loadNamedGroup();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading group took " + duration + " ms");
					handler.onResult(groupItem);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract G loadNamedGroup() throws DbException;

	@Override
	public void loadItems(
			final ResultExceptionHandler<ThreadItemList<I>, DbException> handler) {
		checkGroupId();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					// Load headers
					long now = System.currentTimeMillis();
					Collection<H> headers = loadHeaders();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading headers took " + duration + " ms");

					// Load bodies into cache
					now = System.currentTimeMillis();
					for (H header : headers) {
						if (!bodyCache.containsKey(header.getId())) {
							bodyCache.put(header.getId(),
									loadMessageBody(header));
						}
					}
					duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading bodies took " + duration + " ms");

					// Build and hand over items
					handler.onResult(buildItems(headers));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract Collection<H> loadHeaders() throws DbException;

	@DatabaseExecutor
	protected abstract String loadMessageBody(H header) throws DbException;

	@Override
	public void loadItem(final H header,
			final ResultExceptionHandler<I, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					String body;
					if (!bodyCache.containsKey(header.getId())) {
						body = loadMessageBody(header);
						bodyCache.put(header.getId(), body);
					} else {
						body = bodyCache.get(header.getId());
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading item took " + duration + " ms");
					I item = buildItem(header, body);
					handler.onResult(item);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void markItemRead(I item) {
		markItemsRead(Collections.singletonList(item));
	}

	@Override
	public void markItemsRead(final Collection<I> items) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (I i : items) {
						markRead(i.getId());
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract void markRead(MessageId id) throws DbException;

	protected void storePost(final M msg, final String body,
			final ResultExceptionHandler<I, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					H header = addLocalMessage(msg);
					bodyCache.put(msg.getMessage().getId(), body);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
					resultHandler.onResult(buildItem(header, body));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract H addLocalMessage(M message) throws DbException;

	@Override
	public void deleteNamedGroup(final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					G groupItem = loadNamedGroup();
					deleteNamedGroup(groupItem);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing group took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract void deleteNamedGroup(G groupItem) throws DbException;

	private ThreadItemList<I> buildItems(Collection<H> headers)
			throws DbException {
		ThreadItemList<I> items = new ThreadItemListImpl<>();
		for (H h : headers) {
			items.add(buildItem(h, bodyCache.get(h.getId())));
		}
		MessageId msgId = messageTracker.loadStoredMessageId(groupId);
		if (LOG.isLoggable(INFO))
			LOG.info("Loaded last top visible message id " + msgId);
		items.setFirstVisibleId(msgId);
		return items;
	}

	protected abstract I buildItem(H header, String body);

	protected GroupId getGroupId() {
		checkGroupId();
		return groupId;
	}

	private void checkGroupId() {
		if (groupId == null) throw new IllegalStateException();
	}

}
