package org.briarproject.contact;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.identity.IdentityManager;

import javax.inject.Singleton;

public class ContactModule extends AbstractModule {

	@Override
	protected void configure() {}

	@Provides @Singleton
	ContactManager getContactManager(IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}
}
