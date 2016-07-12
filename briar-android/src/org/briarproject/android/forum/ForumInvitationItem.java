package org.briarproject.android.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.forum.Forum;

import java.util.Collection;

class ForumInvitationItem {

	private final Forum forum;
	private final boolean subscribed;
	private final Collection<Contact> contacts;

	ForumInvitationItem(Forum forum, boolean subscribed,
			Collection<Contact> contacts) {

		this.forum = forum;
		this.subscribed = subscribed;
		this.contacts = contacts;
	}

	Forum getForum() {
		return forum;
	}

	public boolean isSubscribed() {
		return subscribed;
	}

	Collection<Contact> getContacts() {
		return contacts;
	}
}
