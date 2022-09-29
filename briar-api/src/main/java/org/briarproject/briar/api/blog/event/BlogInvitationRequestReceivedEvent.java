package org.briarproject.briar.api.blog.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.conversation.ConversationRequest;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogInvitationRequestReceivedEvent extends
		ConversationMessageReceivedEvent<ConversationRequest<Blog>> {

	public BlogInvitationRequestReceivedEvent(ConversationRequest<Blog> request,
			ContactId contactId) {
		super(request, contactId);
	}

}
