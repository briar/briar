package org.briarproject.android.contact;

import org.briarproject.android.contact.ConversationItem.PartialItem;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageOutItem extends ConversationOutItem
		implements PartialItem {

	ConversationMessageOutItem(PrivateMessageHeader h) {
		super(h.getId(), h.getGroupId(), null, h.getTimestamp(), h.isSent(),
				h.isSeen());
	}

	public void setText(String body) {
		text = body;
	}

}
