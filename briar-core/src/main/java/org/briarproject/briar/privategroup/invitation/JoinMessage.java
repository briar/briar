package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class JoinMessage extends GroupInvitationMessage {

	@Nullable
	private final MessageId previousMessageId;

	JoinMessage(MessageId id, GroupId contactGroupId, GroupId privateGroupId,
			long timestamp, @Nullable MessageId previousMessageId) {
		super(id, contactGroupId, privateGroupId, timestamp);
		this.previousMessageId = previousMessageId;
	}

	@Nullable
	MessageId getPreviousMessageId() {
		return previousMessageId;
	}
}
