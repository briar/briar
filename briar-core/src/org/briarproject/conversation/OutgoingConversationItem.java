package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationItem.OutgoingItem;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class OutgoingConversationItem extends ConversationItemImpl
		implements OutgoingItem {

	private boolean sent, seen;

	public OutgoingConversationItem(MessageId id, long time, boolean sent,
			boolean seen) {
		super(id, time);

		this.sent = sent;
		this.seen = seen;
	}

	@Override
	public boolean isSent() {
		return sent;
	}

	@Override
	public void setSent(boolean sent) {
		this.sent = sent;
	}

	@Override
	public boolean isSeen() {
		return seen;
	}

	@Override
	public void setSeen(boolean seen) {
		this.seen = seen;
	}
}
