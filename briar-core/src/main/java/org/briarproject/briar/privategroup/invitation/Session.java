package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class Session<S extends State> {

	private final GroupId contactGroupId, privateGroupId;
	@Nullable
	private final MessageId lastLocalMessageId, lastRemoteMessageId;
	private final long localTimestamp, inviteTimestamp;

	Session(GroupId contactGroupId, GroupId privateGroupId,
			@Nullable MessageId lastLocalMessageId,
			@Nullable MessageId lastRemoteMessageId, long localTimestamp,
			long inviteTimestamp) {
		this.contactGroupId = contactGroupId;
		this.privateGroupId = privateGroupId;
		this.lastLocalMessageId = lastLocalMessageId;
		this.lastRemoteMessageId = lastRemoteMessageId;
		this.localTimestamp = localTimestamp;
		this.inviteTimestamp = inviteTimestamp;
	}

	abstract Role getRole();

	abstract S getState();

	GroupId getContactGroupId() {
		return contactGroupId;
	}

	GroupId getPrivateGroupId() {
		return privateGroupId;
	}

	@Nullable
	MessageId getLastLocalMessageId() {
		return lastLocalMessageId;
	}

	@Nullable
	MessageId getLastRemoteMessageId() {
		return lastRemoteMessageId;
	}

	long getLocalTimestamp() {
		return localTimestamp;
	}

	long getInviteTimestamp() {
		return inviteTimestamp;
	}
}
