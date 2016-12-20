package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;

@NotNullByDefault
public class BlogInvitationRequest extends InvitationRequest {

	private final String blogAuthorName;

	public BlogInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, String blogAuthorName,
			@Nullable String message, GroupId blogId,
			boolean available, boolean canBeOpened, long time,
			boolean local, boolean sent, boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, message, blogId, available,
				canBeOpened, time, local, sent, seen, read);
		this.blogAuthorName = blogAuthorName;
	}

	public String getBlogAuthorName() {
		return blogAuthorName;
	}

}
