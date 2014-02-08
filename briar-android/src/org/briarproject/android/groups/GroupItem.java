package org.briarproject.android.groups;

import org.briarproject.api.db.MessageHeader;

// This class is not thread-safe
class GroupItem {

	private final MessageHeader header;
	private boolean expanded;
	private byte[] body;

	GroupItem(MessageHeader header) {
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
