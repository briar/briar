package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class BaseMessageHeader {

	private final MessageId id;
	private final GroupId groupId;
	private final long timestamp;
	private final boolean local, sent, seen, read;

	public BaseMessageHeader(MessageId id, GroupId groupId, long timestamp,
			boolean local, boolean sent, boolean seen, boolean read) {

		this.id = id;
		this.groupId = groupId;
		this.timestamp = timestamp;
		this.local = local;
		this.sent = sent;
		this.seen = seen;
		this.read = read;
	}

	public MessageId getId() {
		return id;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isSent() {
		return sent;
	}

	public boolean isSeen() {
		return seen;
	}

	public boolean isRead() {
		return read;
	}

}
