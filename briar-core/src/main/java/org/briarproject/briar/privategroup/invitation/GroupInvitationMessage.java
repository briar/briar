package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class GroupInvitationMessage {

	private final MessageId id;
	private final GroupId contactGroupId, privateGroupId;
	private final long timestamp;

	GroupInvitationMessage(MessageId id, GroupId contactGroupId,
			GroupId privateGroupId, long timestamp) {
		this.id = id;
		this.contactGroupId = contactGroupId;
		this.privateGroupId = privateGroupId;
		this.timestamp = timestamp;
	}

	MessageId getId() {
		return id;
	}

	GroupId getContactGroupId() {
		return contactGroupId;
	}

	GroupId getPrivateGroupId() {
		return privateGroupId;
	}

	long getTimestamp() {
		return timestamp;
	}
}
