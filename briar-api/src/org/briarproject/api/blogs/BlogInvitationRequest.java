package org.briarproject.api.blogs;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sync.MessageId;

public class BlogInvitationRequest extends InvitationRequest {

	private final String blogAuthorName;

	public BlogInvitationRequest(MessageId id, SessionId sessionId,
			ContactId contactId, String blogAuthorName, String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, contactId, message, available, time, local, sent,
				seen, read);
		this.blogAuthorName = blogAuthorName;
	}

	public String getBlogAuthorName() {
		return blogAuthorName;
	}

}
