package org.briarproject.contact;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ContactModule {

	public static class EagerSingletons {
		@Inject ContactManager contactManager;
	}

	@Provides
	@Singleton
	ContactManager getContactManager(LifecycleManager lifecycleManager,
			IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}
}
