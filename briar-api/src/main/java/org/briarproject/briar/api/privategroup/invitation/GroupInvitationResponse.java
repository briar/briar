package org.briarproject.briar.api.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.privategroup.PrivateGroup;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationResponse extends PrivateResponse<PrivateGroup> {

	public GroupInvitationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, PrivateGroup privateGroup, boolean accept) {
		super(id, groupId, time, local, sent, seen, read, sessionId,
				privateGroup, accept);
	}

}
