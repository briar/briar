package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationRequestReceivedEvent extends
		InvitationRequestReceivedEvent<PrivateGroup> {

	public GroupInvitationRequestReceivedEvent(PrivateGroup group,
			ContactId contactId, GroupInvitationRequest request) {
		super(group, contactId, request);
	}

}
