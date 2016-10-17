package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.privategroup.PrivateGroup;

public class GroupInvitationReceivedEvent extends
		InvitationRequestReceivedEvent<PrivateGroup> {

	public GroupInvitationReceivedEvent(PrivateGroup group, ContactId contactId,
			ForumInvitationRequest request) {
		super(group, contactId, request);
	}

}
