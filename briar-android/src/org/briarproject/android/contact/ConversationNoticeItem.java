package org.briarproject.android.contact;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

abstract class ConversationNoticeItem extends ConversationItem {

	private final String text;

	ConversationNoticeItem(MessageId id, GroupId groupId, String text,
			long time) {
		super(id, groupId, time);

		this.text = text;
	}

	public String getText() {
		return text;
	}
}
