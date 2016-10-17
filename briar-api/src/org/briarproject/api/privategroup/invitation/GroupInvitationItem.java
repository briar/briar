package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.api.sharing.Shareable;

public class GroupInvitationItem extends InvitationItem {

	private final Contact creator;

	public GroupInvitationItem(Shareable shareable, boolean subscribed,
			Contact creator) {
		super(shareable, subscribed);

		this.creator = creator;
	}

	public Contact getCreator() {
		return creator;
	}

}
