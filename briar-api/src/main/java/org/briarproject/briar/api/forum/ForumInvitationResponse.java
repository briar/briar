package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateResponse;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationResponse extends PrivateResponse<Forum> {

	public ForumInvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Forum forum, boolean accept) {
		super(id, groupId, time, local, sent, seen, read, sessionId, forum,
				accept);
	}

}
