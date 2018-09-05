package org.briarproject.briar.api.blog.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogInvitationRequestReceivedEvent extends
		PrivateMessageReceivedEvent<PrivateRequest<Blog>> {

	public BlogInvitationRequestReceivedEvent(PrivateRequest<Blog> request,
			ContactId contactId) {
		super(request, contactId);
	}

}
