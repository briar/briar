package org.briarproject.android.contact;

import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.MessageHeader.State;

// This class is not thread-safe
class ConversationItem {

	private final MessageHeader header;
	private byte[] body;
	private State status;

	ConversationItem(MessageHeader header) {
		this.header = header;
		body = null;
		status = header.getStatus();
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

	State getStatus() {
		return status;
	}

	void setStatus(State state) {
		this.status = state;
	}
}
