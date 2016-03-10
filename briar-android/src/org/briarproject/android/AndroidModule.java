package org.briarproject.android;

import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;

import javax.inject.Inject;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidModule {

	static class EagerSingletons {
		@Inject
		AndroidNotificationManager androidNotificationManager;
	}

	@Provides
	@ApplicationScope
	ReferenceManager provideReferenceManager() {
		return new ReferenceManagerImpl();
	}

	@Provides
	@ApplicationScope
	AndroidNotificationManager provideAndroidNotificationManager(
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManagerImpl notificationManager) {
		lifecycleManager.register(notificationManager);
		eventBus.addListener(notificationManager);

		return notificationManager;
	}

}
