package org.briarproject.api.event;

import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.contact.ContactId;

public class BlogInvitationResponseReceivedEvent extends InvitationResponseReceivedEvent {

	private final String blogTitle;

	public BlogInvitationResponseReceivedEvent(String blogTitle,
			ContactId contactId, BlogInvitationResponse response) {
		super(contactId, response);
		this.blogTitle = blogTitle;
	}

	public String getBlogTitle() {
		return blogTitle;
	}
}
