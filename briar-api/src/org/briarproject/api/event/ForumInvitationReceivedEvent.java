package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.introduction.IntroductionRequest;

public class ForumInvitationReceivedEvent extends Event {

	private final Forum forum;
	private final ContactId contactId;

	public ForumInvitationReceivedEvent(Forum forum, ContactId contactId) {
		this.forum = forum;
		this.contactId = contactId;
	}

	public Forum getForum() {
		return forum;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
