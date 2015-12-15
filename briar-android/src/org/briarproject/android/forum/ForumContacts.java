package org.briarproject.android.forum;

import org.briarproject.api.Contact;
import org.briarproject.api.sync.Group;

import java.util.Collection;

class ForumContacts {

	private final Group group;
	private final Collection<Contact> contacts;

	ForumContacts(Group group, Collection<Contact> contacts) {
		this.group = group;
		this.contacts = contacts;
	}

	Group getGroup() {
		return group;
	}

	Collection<Contact> getContacts() {
		return contacts;
	}
}
