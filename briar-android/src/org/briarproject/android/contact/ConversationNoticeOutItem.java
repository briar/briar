package org.briarproject.android.contact;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeOutItem extends ConversationOutItem {

	@Nullable
	private final String msgText;

	ConversationNoticeOutItem(MessageId id, GroupId groupId,
			String text, @Nullable String msgText, long time,
			boolean sent, boolean seen) {
		super(id, groupId, text, time, sent, seen);
		this.msgText = msgText;
	}

	@Nullable
	public String getMsgText() {
		return msgText;
	}

}
