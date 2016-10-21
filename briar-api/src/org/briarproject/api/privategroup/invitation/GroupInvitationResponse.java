package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationResponse extends InvitationResponse {

	public GroupInvitationResponse(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, boolean accept, long time,
			boolean local, boolean sent, boolean seen, boolean read) {
		super(id, sessionId, groupId, contactId, accept, time, local, sent,
				seen, read);
	}
}
