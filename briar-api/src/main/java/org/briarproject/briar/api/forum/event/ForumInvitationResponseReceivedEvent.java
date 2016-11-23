package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

public class ForumInvitationResponseReceivedEvent extends
		InvitationResponseReceivedEvent {

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
