package org.briarproject.sharing;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sharing.SharingMessage;
import org.briarproject.api.sync.MessageId;

public interface InvitationMessageFactory<I extends SharingMessage.Invitation, IM extends InvitationMessage> {

	IM build(MessageId id, I msg, ContactId contactId, boolean available,
			long time, boolean local, boolean sent, boolean seen, boolean read);
}
