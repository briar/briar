package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageInItem extends ConversationInItem {

	ConversationMessageInItem(PrivateMessageHeader h) {
		super(h.getId(), h.getGroupId(), null, h.getTimestamp(), h.isRead());
	}

}
