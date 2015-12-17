package org.briarproject.android.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.forum.Forum;

import java.util.Collection;

class ForumContacts {

	private final Forum forum;
	private final Collection<Contact> contacts;

	ForumContacts(Forum forum, Collection<Contact> contacts) {
		this.forum = forum;
		this.contacts = contacts;
	}

	Forum getForum() {
		return forum;
	}

	Collection<Contact> getContacts() {
		return contacts;
	}
}
