package org.briarproject.android.sharing;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.sharing.Shareable;

import java.util.Collection;

class InvitationItem {

	private final Shareable shareable;
	private final boolean subscribed;
	private final Collection<Contact> contacts;

	InvitationItem(Shareable shareable, boolean subscribed,
			Collection<Contact> contacts) {

		this.shareable = shareable;
		this.subscribed = subscribed;
		this.contacts = contacts;
	}

	Shareable getShareable() {
		return shareable;
	}

	boolean isSubscribed() {
		return subscribed;
	}

	Collection<Contact> getContacts() {
		return contacts;
	}
}
