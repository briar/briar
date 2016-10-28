package org.briarproject.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.R;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeInItem extends ConversationItem {

	@Nullable
	private final String msgText;

	ConversationNoticeInItem(MessageId id, GroupId groupId,
			String text, @Nullable String msgText, long time,
			boolean read) {
		super(id, groupId, text, time, read);
		this.msgText = msgText;
	}

	@Nullable
	public String getMsgText() {
		return msgText;
	}

	@Override
	public boolean isIncoming() {
		return true;
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_notice_in;
	}

}
