package org.briarproject.contact;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;

import javax.inject.Singleton;

public class ContactModule extends AbstractModule {

	@Override
	protected void configure() {}

	@Provides @Singleton
	ContactManager getContactManager(LifecycleManager lifecycleManager,
			IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		lifecycleManager.register(contactManager);
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}
}
