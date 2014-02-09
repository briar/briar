package org.briarproject.android.groups;

import org.briarproject.api.db.MessageHeader;

// This class is not thread-safe
class GroupItem {

	private final MessageHeader header;
	private byte[] body;

	GroupItem(MessageHeader header) {
		this.header = header;
		body = null;
	}

	MessageHeader getHeader() {
		return header;
	}

	byte[] getBody() {
		return body;
	}

	void setBody(byte[] body) {
		this.body = body;
	}
}
