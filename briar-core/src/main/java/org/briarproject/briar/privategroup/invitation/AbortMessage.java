package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class AbortMessage extends GroupInvitationMessage {

	AbortMessage(MessageId id, GroupId contactGroupId, GroupId privateGroupId,
			long timestamp) {
		super(id, contactGroupId, privateGroupId, timestamp);
	}
}
