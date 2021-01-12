package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.BlogManager;

import java.util.concurrent.Executor;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BaseControllerImpl extends DbControllerImpl
		implements EventListener {

	protected final EventBus eventBus;
	protected final AndroidNotificationManager notificationManager;
	protected final IdentityManager identityManager;
	protected final BlogManager blogManager;

	BaseControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager) {
		super(dbExecutor, lifecycleManager);
		this.eventBus = eventBus;
		this.notificationManager = notificationManager;
		this.identityManager = identityManager;
		this.blogManager = blogManager;
	}

}
