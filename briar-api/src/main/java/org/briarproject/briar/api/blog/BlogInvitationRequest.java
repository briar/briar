package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;

@NotNullByDefault
public class BlogInvitationRequest extends InvitationRequest<Blog> {

	public BlogInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Blog blog, @Nullable String text,
			boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, blog,
				text, available, canBeOpened);
	}

	@Override
	public <T> T accept(PrivateMessageVisitor<T> v) {
		return v.visitBlogInvitationRequest(this);
	}
}
