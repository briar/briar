package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateResponse;

@NotNullByDefault
public class BlogInvitationResponse extends PrivateResponse<Blog> {

	public BlogInvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Blog blog, boolean accept) {
		super(id, groupId, time, local, sent, seen, read, sessionId, blog,
				accept);
	}

}
