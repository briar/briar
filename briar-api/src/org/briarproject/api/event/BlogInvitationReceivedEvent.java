package org.briarproject.api.event;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.contact.ContactId;

public class BlogInvitationReceivedEvent extends InvitationReceivedEvent {

	private final Blog blog;

	public BlogInvitationReceivedEvent(Blog blog, ContactId contactId) {
		super(contactId);
		this.blog = blog;
	}

	public Blog getBlog() {
		return blog;
	}
}
