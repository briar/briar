package org.briarproject.android.contact;

import org.briarproject.api.db.MessageHeader;

// This class is not thread-safe
class ConversationItem {

	private final MessageHeader header;
	private byte[] body;
	private boolean delivered;

	ConversationItem(MessageHeader header) {
		this.header = header;
		body = null;
		delivered = header.isDelivered();
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

	boolean isDelivered() {
		return delivered;
	}

	void setDelivered(boolean delivered) {
		this.delivered = delivered;
	}
}
