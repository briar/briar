package org.briarproject.briar.api.blog.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogInvitationResponseReceivedEvent
		extends ConversationMessageReceivedEvent<BlogInvitationResponse> {

	public BlogInvitationResponseReceivedEvent(BlogInvitationResponse response,
			ContactId contactId) {
		super(response, contactId);
	}

}
