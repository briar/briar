package org.briarproject.android.contact;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationOutItem extends ConversationItem {

	private boolean sent, seen;

	ConversationOutItem(MessageId id, GroupId groupId, @Nullable String text,
			long time, boolean sent, boolean seen) {
		super(id, groupId, text, time);

		this.sent = sent;
		this.seen = seen;
	}

	public boolean isSent() {
		return sent;
	}

	public void setSent(boolean sent) {
		this.sent = sent;
	}

	public boolean isSeen() {
		return seen;
	}

	public void setSeen(boolean seen) {
		this.seen = seen;
	}

}
