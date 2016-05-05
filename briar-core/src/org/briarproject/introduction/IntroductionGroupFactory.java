package org.briarproject.introduction;

import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.sync.Group;

import javax.inject.Inject;

public class IntroductionGroupFactory {

	final private PrivateGroupFactory privateGroupFactory;
	final private Group localGroup;

	@Inject
	IntroductionGroupFactory(PrivateGroupFactory privateGroupFactory) {
		this.privateGroupFactory = privateGroupFactory;
		localGroup = privateGroupFactory
				.createLocalGroup(IntroductionManagerImpl.CLIENT_ID);
	}

	public Group createIntroductionGroup(Contact c) {
		return privateGroupFactory
				.createPrivateGroup(IntroductionManagerImpl.CLIENT_ID, c);
	}

	public Group createLocalGroup() {
		return localGroup;
	}

}
