package org.briarproject.briar.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

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
