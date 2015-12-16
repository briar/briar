package org.briarproject.contact;

import com.google.inject.AbstractModule;

import org.briarproject.api.contact.ContactManager;

public class ContactModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ContactManager.class).to(ContactManagerImpl.class);
	}
}
