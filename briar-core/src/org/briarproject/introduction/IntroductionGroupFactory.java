package org.briarproject.introduction;

import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.sync.Group;

import javax.inject.Inject;

public class IntroductionGroupFactory {

	final private ContactGroupFactory contactGroupFactory;
	final private Group localGroup;

	@Inject
	IntroductionGroupFactory(ContactGroupFactory contactGroupFactory) {
		this.contactGroupFactory = contactGroupFactory;
		localGroup = contactGroupFactory
				.createLocalGroup(IntroductionManagerImpl.CLIENT_ID);
	}

	public Group createIntroductionGroup(Contact c) {
		return contactGroupFactory
				.createContactGroup(IntroductionManagerImpl.CLIENT_ID, c);
	}

	public Group createLocalGroup() {
		return localGroup;
	}

}
