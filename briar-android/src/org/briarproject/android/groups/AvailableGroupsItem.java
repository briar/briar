package org.briarproject.android.groups;

import java.util.Collection;

import org.briarproject.api.Contact;
import org.briarproject.api.messaging.Group;

class AvailableGroupsItem {

	private final GroupContacts groupContacts;

	AvailableGroupsItem(GroupContacts groupContacts) {
		this.groupContacts = groupContacts;
	}

	Group getGroup() {
		return groupContacts.getGroup();
	}

	Collection<Contact> getContacts() {
		return groupContacts.getContacts();
	}
}
