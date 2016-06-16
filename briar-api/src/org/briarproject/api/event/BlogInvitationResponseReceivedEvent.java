package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

public class BlogInvitationResponseReceivedEvent extends InvitationResponseReceivedEvent {

	private final String blogTitle;

	public BlogInvitationResponseReceivedEvent(String blogTitle,
			ContactId contactId) {
		super(contactId);
		this.blogTitle = blogTitle;
	}

	public String getBlogTitle() {
		return blogTitle;
	}
}
