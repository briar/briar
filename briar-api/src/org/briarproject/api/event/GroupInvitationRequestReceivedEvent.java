package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.invitation.GroupInvitationRequest;

public class GroupInvitationRequestReceivedEvent extends
		InvitationRequestReceivedEvent<PrivateGroup> {

	public GroupInvitationRequestReceivedEvent(PrivateGroup group,
			ContactId contactId, GroupInvitationRequest request) {
		super(group, contactId, request);
	}

}
