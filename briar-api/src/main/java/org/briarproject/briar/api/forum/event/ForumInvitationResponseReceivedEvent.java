package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationResponseReceivedEvent extends
		PrivateMessageReceivedEvent<ForumInvitationResponse> {

	public ForumInvitationResponseReceivedEvent(
			ForumInvitationResponse response, ContactId contactId) {
		super(response, contactId);
	}

}
