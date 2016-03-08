package org.briarproject.contact;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.identity.IdentityModule;
import org.briarproject.lifecycle.LifecycleModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ContactModule {

	@Provides
	@Singleton
	ContactManager getContactManager(LifecycleManager lifecycleManager,
			IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}
}
