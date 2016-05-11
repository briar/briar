package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;

// This class is not thread-safe
public class ConversationMessageInItem extends ConversationMessageItem
		implements ConversationItem.IncomingItem {

	private boolean read;

	public ConversationMessageInItem(PrivateMessageHeader header) {
		super(header);

		read = header.isRead();
	}

	@Override
	int getType() {
		return MSG_IN;
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
