package org.briarproject.api.blogs;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.MessageId;

public class BlogInvitationMessage extends InvitationMessage {

	private final String blogTitle;

	public BlogInvitationMessage(MessageId id, SessionId sessionId,
			ContactId contactId, String blogTitle, String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, contactId, message, available, time, local, sent,
				seen, read);
		this.blogTitle = blogTitle;
	}

	public String getBlogTitle() {
		return blogTitle;
	}

}
