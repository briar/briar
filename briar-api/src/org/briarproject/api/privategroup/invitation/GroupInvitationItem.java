package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.sharing.InvitationItem;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationItem extends InvitationItem<PrivateGroup> {

	private final Contact creator;

	public GroupInvitationItem(PrivateGroup shareable, boolean subscribed,
			Contact creator) {
		super(shareable, subscribed);

		this.creator = creator;
	}

	public Contact getCreator() {
		return creator;
	}

}
