package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.messaging.PrivateMessageHeader.Status;

// This class is not thread-safe
class ConversationItem {

	private final PrivateMessageHeader header;
	private byte[] body;
	private Status status;

	ConversationItem(PrivateMessageHeader header) {
		this.header = header;
		body = null;
		status = header.getStatus();
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

	Status getStatus() {
		return status;
	}

	void setStatus(Status status) {
		this.status = status;
	}
}
