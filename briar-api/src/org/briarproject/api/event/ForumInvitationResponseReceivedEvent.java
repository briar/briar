package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

public class ForumInvitationResponseReceivedEvent extends InvitationResponseReceivedEvent {

	private final String forumName;

	public ForumInvitationResponseReceivedEvent(String forumName,
			ContactId contactId) {
		super(contactId);
		this.forumName = forumName;
	}

	public String getForumName() {
		return forumName;
	}
}
