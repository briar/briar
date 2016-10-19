package org.briarproject.android.threaded;

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.BaseMessage;
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
import org.briarproject.api.identity.LocalAuthor;
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

public abstract class ThreadListControllerImpl<G extends NamedGroup, I extends ThreadItem, H extends PostHeader, M extends BaseMessage>
		extends DbControllerImpl
		implements ThreadListController<G, I, H>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	private final IdentityManager identityManager;
	private final Executor cryptoExecutor;
	protected final AndroidNotificationManager notificationManager;
	private final EventBus eventBus;

	private final Map<MessageId, String> bodyCache =
			new ConcurrentHashMap<>();

	private volatile GroupId groupId;

	protected ThreadListListener<H> listener;

	protected ThreadListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor, EventBus eventBus,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager);
		this.identityManager = identityManager;
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
		notificationManager.blockNotification(getGroupId());
		eventBus.addListener(this);
	}

	@CallSuper
	@Override
	public void onActivityPause() {
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
						LOG.info(
								"Loading named group took " + duration + " ms");
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
				LOG.info("Loading items...");
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
									loadMessageBody(header.getId()));
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
	protected abstract String loadMessageBody(MessageId id) throws DbException;

	@Override
	public void loadItem(final H header,
			final ResultExceptionHandler<I, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading item...");
				try {
					String body;
					if (!bodyCache.containsKey(header.getId())) {
						body = loadMessageBody(header.getId());
						bodyCache.put(header.getId(), body);
					} else {
						body = bodyCache.get(header.getId());
					}
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

	@Override
	public void createAndStoreMessage(final String body,
			@Nullable final MessageId parentId,
			final ResultExceptionHandler<I, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor author = identityManager.getLocalAuthor();
					long timestamp = getLatestTimestamp();
					timestamp =
							Math.max(timestamp, System.currentTimeMillis());
					createMessage(body, timestamp, parentId, author,
							handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract long getLatestTimestamp() throws DbException;

	private void createMessage(final String body, final long timestamp,
			final @Nullable MessageId parentId, final LocalAuthor author,
			final ResultExceptionHandler<I, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating message...");
				M msg = createLocalMessage(body, timestamp, parentId, author);
				storePost(msg, body, handler);
			}
		});
	}

	@CryptoExecutor
	protected abstract M createLocalMessage(String body, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author);

	private void storePost(final M msg, final String body,
			final ResultExceptionHandler<I, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LOG.info("Store message...");
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
		List<I> entries = new ArrayList<>();
		for (H h : headers) {
			entries.add(buildItem(h, bodyCache.get(h.getId())));
		}
		return entries;
	}

	/**
	 * When building the item, the body can be assumed to be cached
	 */
	protected abstract I buildItem(H header, String body);

	protected GroupId getGroupId() {
		checkGroupId();
		return groupId;
	}

	private void checkGroupId() {
		if (groupId == null) {
			throw new IllegalStateException(
					"You must set the GroupId before the controller is started.");
		}
	}

}
