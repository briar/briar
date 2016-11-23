package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.privategroup.invitation.InviteeState.START;
import static org.briarproject.briar.privategroup.invitation.Role.INVITEE;

@Immutable
@NotNullByDefault
class InviteeSession extends Session<InviteeState> {

	private final InviteeState state;

	InviteeSession(GroupId contactGroupId, GroupId privateGroupId,
			@Nullable MessageId lastLocalMessageId,
			@Nullable MessageId lastRemoteMessageId, long localTimestamp,
			long inviteTimestamp, InviteeState state) {
		super(contactGroupId, privateGroupId, lastLocalMessageId,
				lastRemoteMessageId, localTimestamp, inviteTimestamp);
		this.state = state;
	}

	InviteeSession(GroupId contactGroupId, GroupId privateGroupId) {
		this(contactGroupId, privateGroupId, null, null, 0, 0, START);
	}

	@Override
	Role getRole() {
		return INVITEE;
	}

	@Override
	InviteeState getState() {
		return state;
	}
}
