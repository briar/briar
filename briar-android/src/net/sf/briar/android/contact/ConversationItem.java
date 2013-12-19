package net.sf.briar.android.contact;

import net.sf.briar.api.db.MessageHeader;

// This class is not thread-safe
class ConversationItem {

	private final MessageHeader header;
	private boolean expanded;
	private byte[] body;

	ConversationItem(MessageHeader header) {
		this.header = header;
		expanded = false;
		body = null;
	}

	MessageHeader getHeader() {
		return header;
	}

	boolean isExpanded() {
		return expanded;
	}

	void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	byte[] getBody() {
		return body;
	}

	void setBody(byte[] body) {
		this.body = body;
	}
}
