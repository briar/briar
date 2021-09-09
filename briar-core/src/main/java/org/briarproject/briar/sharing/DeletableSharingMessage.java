package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;

abstract class DeletableSharingMessage extends SharingMessage {

	private final long autoDeleteTimer;

	DeletableSharingMessage(MessageId id, GroupId contactGroupId,
			GroupId shareableId, long timestamp,
			@Nullable MessageId previousMessageId, long autoDeleteTimer) {
		super(id, contactGroupId, shareableId, timestamp, previousMessageId);
		this.autoDeleteTimer = autoDeleteTimer;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}
}
