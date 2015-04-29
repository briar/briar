package org.briarproject.android.groups;

import java.util.Collection;

import org.briarproject.api.Contact;
import org.briarproject.api.messaging.Group;

class GroupContacts {

	private final Group group;
	private final Collection<Contact> contacts;

	GroupContacts(Group group, Collection<Contact> contacts) {
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
