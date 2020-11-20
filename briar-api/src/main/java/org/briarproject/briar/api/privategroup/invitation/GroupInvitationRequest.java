package org.briarproject.briar.api.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationMessageVisitor;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@Immutable
@NotNullByDefault
public class GroupInvitationRequest extends InvitationRequest<PrivateGroup> {

	public GroupInvitationRequest(MessageId id, GroupId groupId, long time,
			boolean local, boolean read, boolean sent, boolean seen,
			SessionId sessionId, PrivateGroup shareable,
			@Nullable String text, boolean available, boolean canBeOpened) {
		super(id, groupId, time, local, read, sent, seen, sessionId, shareable,
				text, available, canBeOpened, NO_AUTO_DELETE_TIMER);
	}

	@Override
	public <T> T accept(ConversationMessageVisitor<T> v) {
		return v.visitGroupInvitationRequest(this);
	}
}
