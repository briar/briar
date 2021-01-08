package org.briarproject.briar.android.threaded;

import android.app.Activity;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.util.concurrent.Executor;

import androidx.annotation.CallSuper;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListControllerImpl<I extends ThreadItem>
		extends DbControllerImpl
		implements ThreadListController<I>, EventListener {

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
		eventBus.addListener(this);
	}

	@CallSuper
	@Override
	public void onActivityStop() {
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {

	}

	@Override
	public void eventOccurred(Event e) {
	}

	protected GroupId getGroupId() {
		checkGroupId();
		return groupId;
	}

	private void checkGroupId() {
		if (groupId == null) throw new IllegalStateException();
	}

}
