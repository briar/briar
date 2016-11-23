package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.privategroup.invitation.CreatorState.START;
import static org.briarproject.briar.privategroup.invitation.Role.CREATOR;

@Immutable
@NotNullByDefault
class CreatorSession extends Session<CreatorState> {

	private final CreatorState state;

	CreatorSession(GroupId contactGroupId, GroupId privateGroupId,
			@Nullable MessageId lastLocalMessageId,
			@Nullable MessageId lastRemoteMessageId, long localTimestamp,
			long inviteTimestamp, CreatorState state) {
		super(contactGroupId, privateGroupId, lastLocalMessageId,
				lastRemoteMessageId, localTimestamp, inviteTimestamp);
		this.state = state;
	}

	CreatorSession(GroupId contactGroupId, GroupId privateGroupId) {
		this(contactGroupId, privateGroupId, null, null, 0, 0, START);
	}

	@Override
	Role getRole() {
		return CREATOR;
	}

	@Override
	CreatorState getState() {
		return state;
	}
}
