package org.briarproject.api.event;

import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.contact.ContactId;

public class BlogInvitationResponseReceivedEvent
		extends InvitationResponseReceivedEvent {

	public BlogInvitationResponseReceivedEvent(ContactId contactId,
			BlogInvitationResponse response) {
		super(contactId, response);
	}

}
