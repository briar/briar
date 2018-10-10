package org.briarproject.briar.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	@Nullable
	protected String text;
	private final MessageId id;
	private final GroupId groupId;
	private final long time;
	private boolean read;

	ConversationItem(MessageId id, GroupId groupId, @Nullable String text,
			long time, boolean read) {
		this.id = id;
		this.groupId = groupId;
		this.text = text;
		this.time = time;
		this.read = read;
	}

	MessageId getId() {
		return id;
	}

	GroupId getGroupId() {
		return groupId;
	}

	void setText(String text) {
		this.text = text;
	}

	@Nullable
	public String getText() {
		return text;
	}

	long getTime() {
		return time;
	}

	public boolean isRead() {
		return read;
	}

	abstract public boolean isIncoming();

	@LayoutRes
	abstract public int getLayout();
}
