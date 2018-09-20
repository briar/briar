package org.briarproject.briar.api.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupInvitationRequest extends InvitationRequest<PrivateGroup> {

	public GroupInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, PrivateGroup shareable,
			@Nullable String message, boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, sent, seen, read, sessionId, shareable,
				message, available, canBeOpened);
	}

	@Override
	public void accept(PrivateMessageVisitor v) {
		v.visitGroupInvitationRequest(this);
	}
}
