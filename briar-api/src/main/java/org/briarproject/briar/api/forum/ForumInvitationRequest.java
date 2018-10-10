package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequest extends InvitationRequest<Forum> {

	public ForumInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Forum forum, @Nullable String text,
			boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, forum,
				text, available, canBeOpened);
	}

	@Override
	public <T> T accept(PrivateMessageVisitor<T> v) {
		return v.visitForumInvitationRequest(this);
	}
}
