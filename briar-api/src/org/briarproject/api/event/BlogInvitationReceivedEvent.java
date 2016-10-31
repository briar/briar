package org.briarproject.api.event;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;

public class BlogInvitationReceivedEvent extends
		InvitationRequestReceivedEvent<Blog> {

	public BlogInvitationReceivedEvent(Blog blog, ContactId contactId,
			InvitationRequest request) {
		super(blog, contactId, request);
	}

}
