package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class AcceptMessage extends SharingMessage {

	AcceptMessage(MessageId id, @Nullable MessageId previousMessageId,
			GroupId contactGroupId, GroupId shareableId, long timestamp) {
		super(id, contactGroupId, shareableId, timestamp, previousMessageId);
	}

}
