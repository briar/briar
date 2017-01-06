package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;

public interface InvitationFactory<S extends Shareable, I extends InvitationResponse> {

	InvitationRequest<S> createInvitationRequest(boolean local, boolean sent,
			boolean seen, boolean read, InviteMessage<S> m, ContactId c,
			boolean available, boolean canBeOpened);

	I createInvitationResponse(MessageId id,
			GroupId contactGroupId, long time, boolean local, boolean sent,
			boolean seen, boolean read, GroupId shareableId,
			ContactId contactId, boolean accept);

}
