package org.briarproject.android.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.sync.Group;

import java.util.Collection;

class AvailableForumsItem {

	private final ForumContacts forumContacts;

	AvailableForumsItem(ForumContacts forumContacts) {
		this.forumContacts = forumContacts;
	}

	Group getGroup() {
		return forumContacts.getGroup();
	}

	Collection<Contact> getContacts() {
		return forumContacts.getContacts();
	}
}
