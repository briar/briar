package org.briarproject.api.sharing;

import org.briarproject.api.contact.Contact;

import java.util.Collection;

public class SharingInvitationItem extends InvitationItem {

	private final Collection<Contact> newSharers;

	public SharingInvitationItem(Shareable shareable, boolean subscribed,
			Collection<Contact> newSharers) {
		super(shareable, subscribed);

		this.newSharers = newSharers;
	}

	public Collection<Contact> getNewSharers() {
		return newSharers;
	}

}
