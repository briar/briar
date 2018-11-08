package org.briarproject.briar.android.conversation;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.conversation.ConversationRequest;
import org.briarproject.briar.api.conversation.ConversationResponse;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeItem extends ConversationItem {

	@Nullable
	private final String msgText;

	ConversationNoticeItem(@LayoutRes int layoutRes, String text,
			ConversationRequest r) {
		super(layoutRes, r);
		this.text = text;
		this.msgText = r.getText();
	}

	ConversationNoticeItem(@LayoutRes int layoutRes, String text,
			ConversationResponse r) {
		super(layoutRes, r);
		this.text = text;
		this.msgText = null;
	}

	@Nullable
	String getMsgText() {
		return msgText;
	}

}
