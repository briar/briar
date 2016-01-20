package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;

// This class is not thread-safe
class ConversationItem {

	private final PrivateMessageHeader header;
	private byte[] body;
	private boolean sent, seen;

	ConversationItem(PrivateMessageHeader header) {
		this.header = header;
		body = null;
		sent = header.isSent();
		seen = header.isSeen();
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

	boolean isSent() {
		return sent;
	}

	void setSent(boolean sent) {
		this.sent = sent;
	}

	boolean isSeen() {
		return seen;
	}

	void setSeen(boolean seen) {
		this.seen = seen;
	}
}
