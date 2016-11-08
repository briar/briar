package org.briarproject.privategroup.invitation;

import org.briarproject.api.sync.GroupId;

class MessageMetadata {

	private final MessageType type;
	private final GroupId privateGroupId;
	private final long timestamp;
	private final boolean local, read, visible, available;

	MessageMetadata(MessageType type, GroupId privateGroupId,
			long timestamp, boolean local, boolean read, boolean visible,
			boolean available) {
		this.privateGroupId = privateGroupId;
		this.type = type;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.visible = visible;
		this.available = available;
	}

	MessageType getMessageType() {
		return type;
	}

	GroupId getPrivateGroupId() {
		return privateGroupId;
	}

	long getTimestamp() {
		return timestamp;
	}

	boolean isLocal() {
		return local;
	}

	boolean isRead() {
		return read;
	}

	boolean isVisibleInConversation() {
		return visible;
	}

	boolean isAvailableToAnswer() {
		return available;
	}
}
