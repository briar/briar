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
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.CallSuper;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListControllerImpl<I extends ThreadItem>
		extends DbControllerImpl
		implements ThreadListController<I>, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ThreadListControllerImpl.class.getName());

	private volatile GroupId groupId;

	private final EventBus eventBus;
	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;

	// UI thread
	protected ThreadListListener<I> listener;

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
		listener = (ThreadListListener<I>) activity;
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
				listener.onGroupRemoved();
			}
		}
	}

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

	protected GroupId getGroupId() {
		checkGroupId();
		return groupId;
	}

	private void checkGroupId() {
		if (groupId == null) throw new IllegalStateException();
	}

}
