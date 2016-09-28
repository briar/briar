package org.briarproject.api.messaging;

import org.briarproject.api.clients.BaseMessageHeader;
import org.briarproject.api.sync.MessageId;

public class PrivateMessageHeader extends BaseMessageHeader {

	private final String contentType;

	public PrivateMessageHeader(MessageId id, long timestamp,
			String contentType, boolean local, boolean read, boolean sent,
			boolean seen) {

		super(id, timestamp, local, read, sent, seen);
		this.contentType = contentType;
	}

	public String getContentType() {
		return contentType;
	}
}
