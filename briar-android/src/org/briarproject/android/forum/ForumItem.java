package org.briarproject.android.forum;

import org.briarproject.api.sync.MessageHeader;

// This class is not thread-safe
class ForumItem {

	private final MessageHeader header;
	private byte[] body;

	ForumItem(MessageHeader header) {
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
