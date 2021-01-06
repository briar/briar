package org.briarproject.briar.android.threaded;

import android.app.Application;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.NamedGroup;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.CallSuper;

import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListViewModel<G extends NamedGroup, I extends ThreadItem>
		extends DbViewModel implements EventListener {

	private static final Logger LOG =
			getLogger(ThreadListViewModel.class.getName());


	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final Executor cryptoExecutor;
	protected final Clock clock;
	private final EventBus eventBus;

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
			EventBus eventBus) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.identityManager = identityManager;
		this.notificationManager = notificationManager;
		this.cryptoExecutor = cryptoExecutor;
		this.clock = clock;
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
	}

}
