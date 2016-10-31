package org.briarproject.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.R;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageOutItem extends ConversationOutItem {

	ConversationMessageOutItem(PrivateMessageHeader h) {
		super(h.getId(), h.getGroupId(), null, h.getTimestamp(), h.isSent(),
				h.isSeen());
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_msg_out;
	}

}
