package org.briarproject.briar.android.hotspot;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
class HotspotViewModel extends DbViewModel {

	@Inject
	HotspotViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
	}

	// TODO copy actual code from Offline Hotspot app

}
