package org.briarproject.android.contact;

import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class ConversationNoticeOutItem extends ConversationNoticeItem
		implements ConversationItem.OutgoingItem {

	private boolean sent, seen;

	ConversationNoticeOutItem(MessageId id, String text, long time,
			boolean sent, boolean seen) {
		super(id, text, time);

		this.sent = sent;
		this.seen = seen;
	}

	@Override
	int getType() {
		return NOTICE_OUT;
	}

	@Override
	public  boolean isSent() {
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
