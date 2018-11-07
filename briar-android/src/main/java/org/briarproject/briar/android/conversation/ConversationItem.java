package org.briarproject.briar.android.conversation;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	@LayoutRes
	private final int layoutRes;
	@Nullable
	protected String text;
	private final MessageId id;
	private final GroupId groupId;
	private final long time;
	private final boolean isIncoming;
	private boolean read, sent, seen;

	ConversationItem(@LayoutRes int layoutRes, ConversationMessageHeader h) {
		this.layoutRes = layoutRes;
		this.text = null;
		this.id = h.getId();
		this.groupId = h.getGroupId();
		this.time = h.getTimestamp();
		this.read = h.isRead();
		this.sent = h.isSent();
		this.seen = h.isSeen();
		this.isIncoming = !h.isLocal();
	}

	@LayoutRes
	public int getLayout() {
		return layoutRes;
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

	/**
	 * Only useful for incoming messages.
	 */
	public boolean isRead() {
		return read;
	}

	/**
	 * Only useful for outgoing messages.
	 */
	boolean isSent() {
		return sent;
	}

	/**
	 * Only useful for outgoing messages.
	 */
	void setSent(boolean sent) {
		this.sent = sent;
	}

	/**
	 * Only useful for outgoing messages.
	 */
	boolean isSeen() {
		return seen;
	}

	/**
	 * Only useful for outgoing messages.
	 */
	void setSeen(boolean seen) {
		this.seen = seen;
	}

	public boolean isIncoming() {
		return isIncoming;
	}

}
