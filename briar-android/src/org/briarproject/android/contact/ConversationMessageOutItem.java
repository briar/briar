package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;

// This class is not thread-safe
class ConversationMessageOutItem extends ConversationMessageItem
		implements ConversationItem.OutgoingItem {

	private boolean sent, seen;

	ConversationMessageOutItem(PrivateMessageHeader header) {
		super(header);

		sent = header.isSent();
		seen = header.isSeen();
	}

	@Override
	int getType() {
		return MSG_OUT;
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
