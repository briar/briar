package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumInvitationRequest;

public class ForumInvitationReceivedEvent extends
		InvitationRequestReceivedEvent<Forum> {

	public ForumInvitationReceivedEvent(Forum forum, ContactId contactId,
			ForumInvitationRequest request) {
		super(forum, contactId, request);
	}

}
