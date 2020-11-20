package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;

import javax.inject.Inject;

public class ForumInvitationFactoryImpl
		implements InvitationFactory<Forum, ForumInvitationResponse> {

	@Inject
	ForumInvitationFactoryImpl() {
	}

	@Override
	public ForumInvitationRequest createInvitationRequest(boolean local,
			boolean sent, boolean seen, boolean read, InviteMessage<Forum> m,
			ContactId c, boolean available, boolean canBeOpened,
			long autoDeleteTimer) {
		SessionId sessionId = new SessionId(m.getShareableId().getBytes());
		return new ForumInvitationRequest(m.getId(), m.getContactGroupId(),
				m.getTimestamp(), local, read, sent, seen, sessionId,
				m.getShareable(), m.getText(), available, canBeOpened,
				autoDeleteTimer);
	}

	@Override
	public ForumInvitationResponse createInvitationResponse(MessageId id,
			GroupId contactGroupId, long time, boolean local, boolean sent,
			boolean seen, boolean read, boolean accept, GroupId shareableId,
			long autoDeleteTimer) {
		SessionId sessionId = new SessionId(shareableId.getBytes());
		return new ForumInvitationResponse(id, contactGroupId, time, local,
				read, sent, seen, sessionId, accept, shareableId,
				autoDeleteTimer);
	}

}
