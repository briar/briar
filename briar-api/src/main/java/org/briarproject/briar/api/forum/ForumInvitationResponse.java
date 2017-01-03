package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationResponse;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationResponse extends InvitationResponse {

	public ForumInvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, GroupId forumId, ContactId contactId,
			boolean accept) {
		super(id, groupId, time, local, sent, seen, read, sessionId, forumId,
				contactId, accept);
	}

}
