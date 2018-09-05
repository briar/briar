package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.sharing.Shareable;

public interface InvitationFactory<S extends Shareable, I extends PrivateResponse<S>> {

	PrivateRequest<S> createInvitationRequest(boolean local, boolean sent,
			boolean seen, boolean read, InviteMessage<S> m, ContactId c,
			boolean available, boolean canBeOpened);

	I createInvitationResponse(MessageId id,
			GroupId contactGroupId, long time, boolean local, boolean sent,
			boolean seen, boolean read, S shareable, boolean accept);

}
