package org.briarproject.briar.autodelete;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AutoDeleteModule {

	public static class EagerSingletons {
		@Inject
		AutoDeleteManager autoDeleteManager;
	}

	@Provides
	@Singleton
	AutoDeleteManager provideAutoDeleteManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			AutoDeleteManagerImpl autoDeleteManager) {
		lifecycleManager.registerOpenDatabaseHook(autoDeleteManager);
		contactManager.registerContactHook(autoDeleteManager);
		// Don't need to register with the client versioning manager as this
		// client's groups aren't shared with contacts
		return autoDeleteManager;
	}
}
