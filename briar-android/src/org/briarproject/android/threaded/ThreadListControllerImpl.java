package org.briarproject.android.threaded;

import android.app.Activity;
import android.support.annotation.CallSuper;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.ThreadedMessage;
import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public abstract class ThreadListControllerImpl<G extends NamedGroup, I extends ThreadItem, H extends PostHeader, M extends ThreadedMessage>
		extends DbControllerImpl
		implements ThreadListController<G, I, H>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	protected final IdentityManager identityManager;
	protected final Executor cryptoExecutor;
	protected final AndroidNotificationManager notificationManager;
	protected final Clock clock;
	private final EventBus eventBus;

	private final Map<MessageId, String> bodyCache = new ConcurrentHashMap<>();

	private volatile GroupId groupId;

	protected volatile ThreadListListener<H> listener;

	protected ThreadListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor, EventBus eventBus,
			Clock clock, AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager);
		this.identityManager = identityManager;
		this.cryptoExecutor = cryptoExecutor;
		this.notificationManager = notificationManager;
		this.clock = clock;
		this.eventBus = eventBus;
	}

	@Override
	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
	}

	@CallSuper
	@SuppressWarnings("unchecked")
	@Override
	public void onActivityCreate(Activity activity) {
		listener = (ThreadListListener<H>) activity;
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
			final ResultExceptionHandler<Collection<I>, DbException> handler) {
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
	public void deleteNamedGroup(
			final ResultExceptionHandler<Void, DbException> handler) {
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
					//noinspection ConstantConditions
					handler.onResult(null);
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

	private List<I> buildItems(Collection<H> headers) {
		List<I> items = new ArrayList<>();
		for (H h : headers) {
			items.add(buildItem(h, bodyCache.get(h.getId())));
		}
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
