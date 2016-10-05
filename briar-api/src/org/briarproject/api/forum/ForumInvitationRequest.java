package org.briarproject.api.forum;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

public class ForumInvitationRequest extends InvitationRequest {

	private final String forumName;

	public ForumInvitationRequest(@Nullable MessageId id, SessionId sessionId,
			ContactId contactId, String forumName, String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, contactId, message, available, time, local, sent,
				seen, read);
		this.forumName = forumName;
	}

	public String getForumName() {
		return forumName;
	}

}
