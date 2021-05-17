package org.briarproject.briar.android.hotspot;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
class HotspotViewModel extends DbViewModel {

	private final AndroidNotificationManager notificationManager;

	@Inject
	HotspotViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			AndroidNotificationManager notificationManager) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.notificationManager = notificationManager;
	}

	void startHotspot() {
		notificationManager.showHotspotNotification();
	}

	private void stopHotspot() {
		notificationManager.clearHotspotNotification();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		stopHotspot();
	}

	// TODO copy actual code from Offline Hotspot app

}
