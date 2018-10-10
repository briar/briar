package org.briarproject.briar.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.PrivateResponse;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeOutItem extends ConversationOutItem {

	@Nullable
	private final String msgText;

	ConversationNoticeOutItem(String text, PrivateRequest r) {
		super(r.getId(), r.getGroupId(), text, r.getTimestamp(), r.isSent(),
				r.isSeen());
		this.msgText = r.getText();
	}

	ConversationNoticeOutItem(String text, PrivateResponse r) {
		super(r.getId(), r.getGroupId(), text, r.getTimestamp(), r.isSent(),
				r.isSeen());
		this.msgText = null;
	}

	@Nullable
	String getMsgText() {
		return msgText;
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_notice_out;
	}
}
