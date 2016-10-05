package org.briarproject.api.event;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;

public class BlogInvitationReceivedEvent extends InvitationReceivedEvent {

	private final Blog blog;

	public BlogInvitationReceivedEvent(Blog blog, ContactId contactId,
			InvitationRequest request) {
		super(contactId, request);
		this.blog = blog;
	}

	public Blog getBlog() {
		return blog;
	}
}
