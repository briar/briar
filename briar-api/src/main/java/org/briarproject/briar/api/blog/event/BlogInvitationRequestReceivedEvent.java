package org.briarproject.briar.api.blog.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogInvitationRequestReceivedEvent extends
		InvitationRequestReceivedEvent<Blog> {

	public BlogInvitationRequestReceivedEvent(Blog blog, ContactId contactId,
			PrivateRequest<Blog> request) {
		super(blog, contactId, request);
	}

}
