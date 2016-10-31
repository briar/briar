package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.invitation.GroupInvitationRequest;

public class GroupInvitationReceivedEvent extends
		InvitationRequestReceivedEvent<PrivateGroup> {

	public GroupInvitationReceivedEvent(PrivateGroup group, ContactId contactId,
			GroupInvitationRequest request) {
		super(group, contactId, request);
	}

}
