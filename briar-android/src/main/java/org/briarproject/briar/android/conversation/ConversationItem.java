package org.briarproject.briar.android.conversation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import androidx.annotation.LayoutRes;

import static org.briarproject.bramble.util.StringUtils.toHexString;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	@LayoutRes
	private final int layoutRes;
	@Nullable
	protected String text;
	private final MessageId id;
	private final GroupId groupId;
	private final long time, autoDeleteTimer;
	private final boolean isIncoming;
	private boolean read, sent, seen;

	ConversationItem(@LayoutRes int layoutRes, ConversationMessageHeader h) {
		this.layoutRes = layoutRes;
		this.text = null;
		this.id = h.getId();
		this.groupId = h.getGroupId();
		this.time = h.getTimestamp();
		this.autoDeleteTimer = h.getAutoDeleteTimer();
		this.read = h.isRead();
		this.sent = h.isSent();
		this.seen = h.isSeen();
		this.isIncoming = !h.isLocal();
	}

	@LayoutRes
	int getLayout() {
		return layoutRes;
	}

	MessageId getId() {
		return id;
	}

	String getKey() {
		return toHexString(id.getBytes());
	}

	GroupId getGroupId() {
		return groupId;
	}

	void setText(String text) {
		this.text = text;
	}

	@Nullable
	String getText() {
		return text;
	}

	long getTime() {
		return time;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}

	/**
	 * Only useful for incoming messages.
	 */
	boolean isRead() {
		return read;
	}

	void markRead() {
		read = true;
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

	boolean isIncoming() {
		return isIncoming;
	}

}
