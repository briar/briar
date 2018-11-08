package org.briarproject.briar.android.conversation;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationMessageItem extends ConversationItem {

	private final List<AttachmentHeader> attachments;

	ConversationMessageItem(@LayoutRes int layoutRes, PrivateMessageHeader h) {
		super(layoutRes, h);
		this.attachments = h.getAttachmentHeaders();
	}

	List<AttachmentHeader> getAttachments() {
		return attachments;
	}

}
