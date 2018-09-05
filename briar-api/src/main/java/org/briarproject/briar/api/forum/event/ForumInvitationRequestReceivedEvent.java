package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequestReceivedEvent extends
		PrivateMessageReceivedEvent<PrivateRequest<Forum>> {

	public ForumInvitationRequestReceivedEvent(PrivateRequest<Forum> request,
			ContactId contactId) {
		super(request, contactId);
	}

}
