package org.briarproject.android.contact;

import org.briarproject.api.sync.MessageId;

public class ConversationNoticeInItem extends ConversationNoticeItem implements
		ConversationItem.IncomingItem {

	private boolean read;

	public ConversationNoticeInItem(MessageId id, String text, long time,
			boolean read) {
		super(id, text, time);

		this.read = read;
	}

	@Override
	int getType() {
		return NOTICE_IN;
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
