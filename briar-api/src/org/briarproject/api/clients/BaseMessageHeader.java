package org.briarproject.api.clients;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

public abstract class BaseMessageHeader {

	private final MessageId id;
	private final GroupId groupId;
	private final long timestamp;
	private final boolean local, read, sent, seen;

	public BaseMessageHeader(@NotNull MessageId id, @NotNull GroupId groupId,
			long timestamp, boolean local, boolean read, boolean sent,
			boolean seen) {

		this.id = id;
		this.groupId = groupId;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.sent = sent;
		this.seen = seen;
	}

	@NotNull
	public MessageId getId() {
		return id;
	}

	@NotNull
	public GroupId getGroupId() {
		return groupId;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isRead() {
		return read;
	}

	public boolean isSent() {
		return sent;
	}

	public boolean isSeen() {
		return seen;
	}

}
