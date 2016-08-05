package org.briarproject.api.sharing;

import org.briarproject.api.contact.Contact;

import java.util.Collection;

public class InvitationItem {

	private final Shareable shareable;
	private final boolean subscribed;
	private final Collection<Contact> newSharers;

	public InvitationItem(Shareable shareable, boolean subscribed,
			Collection<Contact> newSharers) {

		this.shareable = shareable;
		this.subscribed = subscribed;
		this.newSharers = newSharers;
	}

	public Shareable getShareable() {
		return shareable;
	}

	public boolean isSubscribed() {
		return subscribed;
	}

	public Collection<Contact> getNewSharers() {
		return newSharers;
	}
}
