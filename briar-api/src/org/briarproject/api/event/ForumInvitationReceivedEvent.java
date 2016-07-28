package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.Forum;

public class ForumInvitationReceivedEvent extends InvitationReceivedEvent {

	private final Forum forum;

	public ForumInvitationReceivedEvent(Forum forum, ContactId contactId) {
		super(contactId);
		this.forum = forum;
	}

	public Forum getForum() {
		return forum;
	}
}
