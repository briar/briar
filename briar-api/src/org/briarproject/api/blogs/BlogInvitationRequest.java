package org.briarproject.api.blogs;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public class BlogInvitationRequest extends InvitationRequest {

	private final String blogAuthorName;

	public BlogInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, String blogAuthorName,
			String message, boolean available, long time, boolean local,
			boolean sent, boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, message, available, time,
				local, sent, seen, read);
		this.blogAuthorName = blogAuthorName;
	}

	public String getBlogAuthorName() {
		return blogAuthorName;
	}

}
