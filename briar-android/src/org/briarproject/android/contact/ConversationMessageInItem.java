package org.briarproject.android.contact;

import org.briarproject.android.contact.ConversationItem.PartialItem;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageInItem extends ConversationInItem
		implements PartialItem {

	ConversationMessageInItem(PrivateMessageHeader h) {
		super(h.getId(), h.getGroupId(), null, h.getTimestamp(), h.isRead());
	}

	public void setText(String body) {
		text = body;
	}

}
