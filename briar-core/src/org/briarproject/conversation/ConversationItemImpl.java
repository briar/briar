package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.sync.MessageId;

public abstract class ConversationItemImpl implements ConversationItem {

	private final MessageId id;
	private final long time;

	public ConversationItemImpl(MessageId id, long time) {
		this.id = id;
		this.time = time;
	}

	public MessageId getId() {
		return id;
	}

	public long getTime() {
		return time;
	}
}
