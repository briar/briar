package org.briarproject.briar.android.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageItem extends ConversationItem {

	@Nullable
	private List<AttachmentItem> attachments;

	ConversationMessageItem(@LayoutRes int layoutRes, PrivateMessageHeader h,
			@Nullable List<AttachmentItem> attachments) {
		super(layoutRes, h);
		this.attachments = attachments;
	}

	@Nullable
	List<AttachmentItem> getAttachments() {
		return attachments;
	}

	void setAttachments(List<AttachmentItem> attachments) {
		this.attachments = attachments;
	}

}
