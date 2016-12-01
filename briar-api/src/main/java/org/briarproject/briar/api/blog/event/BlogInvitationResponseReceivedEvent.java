package org.briarproject.briar.api.blog.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogInvitationResponseReceivedEvent
		extends InvitationResponseReceivedEvent {

	public BlogInvitationResponseReceivedEvent(ContactId contactId,
			BlogInvitationResponse response) {
		super(contactId, response);
	}

}
