package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationResponseReceivedEvent extends
		InvitationResponseReceivedEvent {

	public ForumInvitationResponseReceivedEvent(ContactId contactId,
			ForumInvitationResponse response) {
		super(contactId, response);
	}

}
