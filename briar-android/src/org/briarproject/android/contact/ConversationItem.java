package org.briarproject.android.contact;

import org.briarproject.api.db.MessageHeader;

// This class is not thread-safe
class ConversationItem {

	public enum State { STORED, SENT, DELIVERED };

	private final MessageHeader header;
	private byte[] body;
	private State status;

	ConversationItem(MessageHeader header) {
		this.header = header;
		body = null;
		status = header.isDelivered() ? State.DELIVERED : State.STORED;
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
