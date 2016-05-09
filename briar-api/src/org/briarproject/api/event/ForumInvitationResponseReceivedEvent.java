package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

public class ForumInvitationResponseReceivedEvent extends Event {

	private final String forumName;
	private final ContactId contactId;

	public ForumInvitationResponseReceivedEvent(String forumName,
			ContactId contactId) {

		this.forumName = forumName;
		this.contactId = contactId;
	}

	public String getForumName() {
		return forumName;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
