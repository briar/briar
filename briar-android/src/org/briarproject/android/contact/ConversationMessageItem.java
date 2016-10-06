package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;

// This class is not thread-safe
abstract class ConversationMessageItem extends ConversationItem {

	private final PrivateMessageHeader header;
	private byte[] body;

	ConversationMessageItem(PrivateMessageHeader header) {
		super(header.getId(), header.getTimestamp());

		this.header = header;
		body = null;
	}

	PrivateMessageHeader getHeader() {
		return header;
	}

	byte[] getBody() {
		return body;
	}

	void setBody(byte[] body) {
		this.body = body;
	}
}
