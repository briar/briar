package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationItem.IncomingItem;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class IncomingConversationItem extends ConversationItemImpl
		implements IncomingItem {

	private boolean read;

	public IncomingConversationItem(MessageId id, long time, boolean read) {
		super(id, time);

		this.read = read;
	}

	@Override
	public boolean isRead() {
		return read;
	}

	@Override
	public void setRead(boolean read) {
		this.read = read;
	}
}
