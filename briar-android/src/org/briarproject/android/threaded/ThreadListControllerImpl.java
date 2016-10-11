package org.briarproject.android.threaded;

import android.app.Activity;
import android.support.annotation.CallSuper;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.IdentityManager;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public abstract class ThreadListControllerImpl<G extends BaseGroup, I extends ThreadItem, H extends PostHeader>
		extends DbControllerImpl
		implements ThreadListController<G, I, H>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	protected final Executor cryptoExecutor;
	protected final CryptoComponent crypto;
	protected final EventBus eventBus;
	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;

	protected final Map<MessageId, String> bodyCache =
			new ConcurrentHashMap<>();
	protected final AtomicLong newestTimeStamp = new AtomicLong();

	protected volatile GroupId groupId;

	protected ThreadListListener<H> listener;

	protected ThreadListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			@CryptoExecutor Executor cryptoExecutor, CryptoComponent crypto,
			EventBus eventBus, IdentityManager identityManager,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager);
		this.cryptoExecutor = cryptoExecutor;
		this.crypto = crypto;
		this.eventBus = eventBus;
		this.identityManager = identityManager;
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

					// Update timestamp of newest item
					updateNewestTimeStamp(headers);

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

	private void updateNewestTimeStamp(Collection<H> headers) {
		for (H h : headers) {
			updateNewestTimestamp(h.getTimestamp());
		}
	}

	protected void updateNewestTimestamp(long update) {
		long newest = newestTimeStamp.get();
		while (newest < update) {
			if (newestTimeStamp.compareAndSet(newest, update)) return;
			newest = newestTimeStamp.get();
		}
	}

	private void checkGroupId() {
		if (groupId == null) {
			throw new IllegalStateException(
					"You must set the GroupId before the controller is started.");
		}
	}

}
