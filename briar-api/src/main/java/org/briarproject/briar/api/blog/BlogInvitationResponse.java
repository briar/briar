package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationResponse;

@NotNullByDefault
public class BlogInvitationResponse extends InvitationResponse {

	public BlogInvitationResponse(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, GroupId blogId,
			boolean accept, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, blogId, accept, time, local,
				sent, seen, read);
	}

}
