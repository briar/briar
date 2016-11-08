package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationResponse;

public class GroupInvitationResponseReceivedEvent
		extends InvitationResponseReceivedEvent {

	public GroupInvitationResponseReceivedEvent(ContactId contactId,
			InvitationResponse response) {
		super(contactId, response);
	}
}
