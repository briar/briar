package org.briarproject.privategroup.invitation;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

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
