package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationResponseReceivedEvent
		extends InvitationResponseReceivedEvent {

	public GroupInvitationResponseReceivedEvent(ContactId contactId,
			InvitationResponse response) {
		super(contactId, response);
	}
}
