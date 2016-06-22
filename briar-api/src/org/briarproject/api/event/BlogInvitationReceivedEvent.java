package org.briarproject.api.event;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.MessageId;

public class BlogInvitationReceivedEvent extends InvitationReceivedEvent {

	private final Blog blog;

	public BlogInvitationReceivedEvent(ContactId contactId, MessageId messageId,
			Blog blog) {
		super(contactId, messageId);
		this.blog = blog;
	}

	public Blog getBlog() {
		return blog;
	}
}
