package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.sync.Group;

import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;

class IntroductionGroupFactory {

	private final ContactGroupFactory contactGroupFactory;
	private final Group localGroup;

	@Inject
	IntroductionGroupFactory(ContactGroupFactory contactGroupFactory) {
		this.contactGroupFactory = contactGroupFactory;
		localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID);
	}

	Group createIntroductionGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID, c);
	}

	public Group createLocalGroup() {
		return localGroup;
	}

}
