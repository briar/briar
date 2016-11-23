package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.privategroup.invitation.PeerState.START;
import static org.briarproject.briar.privategroup.invitation.Role.PEER;

@Immutable
@NotNullByDefault
class PeerSession extends Session<PeerState> {

	private final PeerState state;

	PeerSession(GroupId contactGroupId, GroupId privateGroupId,
			@Nullable MessageId lastLocalMessageId,
			@Nullable MessageId lastRemoteMessageId, long localTimestamp,
			PeerState state) {
		super(contactGroupId, privateGroupId, lastLocalMessageId,
				lastRemoteMessageId, localTimestamp, 0);
		this.state = state;
	}

	PeerSession(GroupId contactGroupId, GroupId privateGroupId) {
		this(contactGroupId, privateGroupId, null, null, 0, START);
	}

	@Override
	Role getRole() {
		return PEER;
	}

	@Override
	PeerState getState() {
		return state;
	}
}
