package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.sharing.InvitationResponse;

@NotNullByDefault
public class BlogInvitationResponse extends InvitationResponse {

	public BlogInvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, boolean accept, GroupId shareableId) {
		super(id, groupId, time, local, sent, seen, read, sessionId,
				accept, shareableId);
	}

	@Override
	public <T> T accept(PrivateMessageVisitor<T> v) {
		return v.visitBlogInvitationResponse(this);
	}
}
