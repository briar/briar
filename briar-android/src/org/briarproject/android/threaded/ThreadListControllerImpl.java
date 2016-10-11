package org.briarproject.android.threaded;

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.clients.BaseMessage;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

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

public abstract class ThreadListControllerImpl<G extends BaseGroup, I extends ThreadItem, H extends PostHeader, M extends BaseMessage>
		extends DbControllerImpl
		implements ThreadListController<G, I, H>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	protected final Executor cryptoExecutor;
	protected final AndroidNotificationManager notificationManager;
	private final EventBus eventBus;

	protected final Map<MessageId, String> bodyCache =
			new ConcurrentHashMap<>();

	protected volatile GroupId groupId;

	protected ThreadListListener<H> listener;

	protected ThreadListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			@CryptoExecutor Executor cryptoExecutor, EventBus eventBus,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager);
		this.cryptoExecutor = cryptoExecutor;
		this.eventBus = eventBus;
		this.notificationManager = notificationManager;
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
	public void onActivityResume() {
		checkGroupId();
		notificationManager.blockNotification(groupId);
		eventBus.addListener(this);
	}

	@CallSuper
	@Override
	public void onActivityPause() {
		notificationManager.unblockNotification(groupId);
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
			if (s.getGroup().getId().equals(groupId)) {
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
	public void loadGroupItem(
			final ResultExceptionHandler<G, DbException> handler) {
		checkGroupId();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					G groupItem = loadGroupItem();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading forum took " + duration + " ms");
					handler.onResult(groupItem);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract G loadGroupItem() throws DbException;

	@Override
	public void loadItems(
			final ResultExceptionHandler<Collection<I>, DbException> handler) {
		checkGroupId();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading items...");
				try {
					// Load headers
					long now = System.currentTimeMillis();
					Collection<H> headers = loadHeaders();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading headers took " + duration + " ms");

					// Load bodies
					now = System.currentTimeMillis();
					loadBodies(headers);
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

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract Collection<H> loadHeaders() throws DbException;

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract void loadBodies(Collection<H> headers)
			throws DbException;

	@Override
	public void loadItem(final H header,
			final ResultExceptionHandler<I, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading item...");
				try {
					loadBodies(Collections.singletonList(header));
					I item = buildItem(header);
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

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract void markRead(MessageId id) throws DbException;

	@Override
	public void send(String body,
			ResultExceptionHandler<I, DbException> resultHandler) {
		send(body, null, resultHandler);
	}

	@Override
	public void send(final String body, @Nullable final MessageId parentId,
			final ResultExceptionHandler<I, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating message...");
				try {
					M msg = createLocalMessage(groupId, body, parentId);
					bodyCache.put(msg.getMessage().getId(), body);
					storePost(msg, handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract M createLocalMessage(GroupId g, String body,
			@Nullable MessageId parentId) throws DbException;

	private void storePost(final M p,
			final ResultExceptionHandler<I, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LOG.info("Store message...");
					long now = System.currentTimeMillis();
					H h = addLocalMessage(p);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
					resultHandler.onResult(buildItem(h));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract H addLocalMessage(M message) throws DbException;

	@Override
	public void deleteGroupItem(
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					G groupItem = loadGroupItem();
					deleteGroupItem(groupItem);
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

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	protected abstract void deleteGroupItem(G groupItem) throws DbException;

	private List<I> buildItems(Collection<H> headers) {
		List<I> entries = new ArrayList<>();
		for (H h : headers) {
			entries.add(buildItem(h));
		}
		return entries;
	}

	/**
	 * When building the item, the body can be assumed to be cached
	 */
	protected abstract I buildItem(H header);

	private void checkGroupId() {
		if (groupId == null) {
			throw new IllegalStateException(
					"You must set the GroupId before the controller is started.");
		}
	}

}
