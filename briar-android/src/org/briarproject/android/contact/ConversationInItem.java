package org.briarproject.android.contact;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationInItem extends ConversationItem {

	private boolean read;

	ConversationInItem(MessageId id, GroupId groupId, @Nullable String text,
			long time, boolean read) {
		super(id, groupId, text, time);

		this.read = read;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

}
