package org.briarproject.android.forum;

import org.briarproject.api.forum.ForumPostHeader;

// This class is not thread-safe
class ForumItem {

	private final ForumPostHeader header;
	private byte[] body;

	ForumItem(ForumPostHeader header) {
		this.header = header;
		body = null;
	}

	ForumPostHeader getHeader() {
		return header;
	}

	byte[] getBody() {
		return body;
	}

	void setBody(byte[] body) {
		this.body = body;
	}
}
