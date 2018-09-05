package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationResponseReceivedEvent
		extends InvitationResponseReceivedEvent<PrivateGroup> {

	public GroupInvitationResponseReceivedEvent(ContactId contactId,
			PrivateResponse<PrivateGroup> response) {
		super(contactId, response);
	}
}
