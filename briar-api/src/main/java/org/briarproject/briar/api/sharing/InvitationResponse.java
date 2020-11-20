package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationResponse;

public abstract class InvitationResponse extends ConversationResponse {

	private final GroupId shareableId;

	public InvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean read, boolean sent, boolean seen,
			SessionId sessionId, boolean accepted, GroupId shareableId,
			long autoDeleteTimer) {
		super(id, groupId, time, local, read, sent, seen, sessionId, accepted,
				autoDeleteTimer);
		this.shareableId = shareableId;
	}

	public GroupId getShareableId() {
		return shareableId;
	}

}
