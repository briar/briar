package org.briarproject.api.forum;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.MessageId;

public class ForumInvitationResponse extends InvitationResponse {

	public ForumInvitationResponse(MessageId id, SessionId sessionId,
			ContactId contactId, boolean accept, long time, boolean local,
			boolean sent, boolean seen, boolean read) {

		super(id, sessionId, contactId, accept, time, local, sent, seen, read);
	}

}
