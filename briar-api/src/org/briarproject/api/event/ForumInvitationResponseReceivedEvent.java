package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.ForumInvitationResponse;

public class ForumInvitationResponseReceivedEvent extends InvitationResponseReceivedEvent {

	private final String forumName;

	public ForumInvitationResponseReceivedEvent(String forumName,
			ContactId contactId, ForumInvitationResponse response) {
		super(contactId, response);
		this.forumName = forumName;
	}

	public String getForumName() {
		return forumName;
	}
}
