package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class DeletableGroupInvitationMessage extends GroupInvitationMessage {

	private final long autoDeleteTimer;

	DeletableGroupInvitationMessage(MessageId id, GroupId contactGroupId,
			GroupId privateGroupId, long timestamp, long autoDeleteTimer) {
		super(id, contactGroupId, privateGroupId, timestamp);
		this.autoDeleteTimer = autoDeleteTimer;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}
}
