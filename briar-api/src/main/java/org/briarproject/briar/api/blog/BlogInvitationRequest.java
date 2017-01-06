package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;

@NotNullByDefault
public class BlogInvitationRequest extends InvitationRequest<Blog> {

	public BlogInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Blog blog, ContactId contactId,
			@Nullable String message, boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, blog,
				contactId, message, available, canBeOpened);
	}

	public String getBlogAuthorName() {
		return getShareable().getName();
	}

}
