package org.briarproject.android.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.forum.Forum;

import java.util.Collection;

class AvailableForumsItem {

	private final ForumContacts forumContacts;

	AvailableForumsItem(ForumContacts forumContacts) {
		this.forumContacts = forumContacts;
	}

	Forum getForum() {
		return forumContacts.getForum();
	}

	Collection<Contact> getContacts() {
		return forumContacts.getContacts();
	}
}
