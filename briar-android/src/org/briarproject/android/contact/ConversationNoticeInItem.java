package org.briarproject.android.contact;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class ConversationNoticeInItem extends ConversationNoticeItem
		implements ConversationItem.IncomingItem {

	private boolean read;

	ConversationNoticeInItem(MessageId id, GroupId groupId, String text,
			long time, boolean read) {
		super(id, groupId, text, time);

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
