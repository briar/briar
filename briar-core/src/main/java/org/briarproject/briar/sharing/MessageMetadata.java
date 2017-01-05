package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class MessageMetadata {

	private final MessageType type;
	private final GroupId shareableId;
	private final long timestamp;
	private final boolean local, read, visible, available, accepted;

	MessageMetadata(MessageType type, GroupId shareableId, long timestamp,
			boolean local, boolean read, boolean visible, boolean available,
			boolean accepted) {
		this.shareableId = shareableId;
		this.type = type;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.visible = visible;
		this.available = available;
		this.accepted = accepted;
	}

	MessageType getMessageType() {
		return type;
	}

	GroupId getShareableId() {
		return shareableId;
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

	/**
	 * Returns true if the invitation was accepted.
	 *
	 * Only applies to messages of type INVITE.
	 */
	public boolean wasAccepted() {
		return accepted;
	}

}
