package net.sf.briar.android.messages;

import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.messaging.MessageId;

class ConversationItem {

	private final PrivateMessageHeader header;
	private final byte[] body;
	private final boolean expanded;

	ConversationItem(PrivateMessageHeader header) {
		this.header = header;
		body = null;
		expanded = false;
	}

	// Collapse an existing item
	ConversationItem(ConversationItem item) {
		this.header = item.header;
		body = null;
		expanded = false;
	}

	// Expand an existing item
	ConversationItem(ConversationItem item, byte[] body) {
		this.header = item.header;
		this.body = body;
		expanded = true;
	}

	MessageId getId() {
		return header.getId();
	}

	String getContentType() {
		return header.getContentType();
	}

	String getSubject() {
		return header.getSubject();
	}

	long getTimestamp() {
		return header.getTimestamp();
	}

	boolean isRead() {
		return header.isRead();
	}

	boolean isStarred() {
		return header.isStarred();
	}

	boolean isIncoming() {
		return header.isIncoming();
	}

	byte[] getBody() {
		return body;
	}

	boolean isExpanded() {
		return expanded;
	}
}
