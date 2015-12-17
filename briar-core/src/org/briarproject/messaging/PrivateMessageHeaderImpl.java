package org.briarproject.messaging;

import org.briarproject.api.identity.Author;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

// Temporary facade during sync protocol refactoring
public class PrivateMessageHeaderImpl implements PrivateMessageHeader {

	private final MessageHeader messageHeader;

	PrivateMessageHeaderImpl(MessageHeader messageHeader) {
		this.messageHeader = messageHeader;
	}

	@Override
	public MessageId getId() {
		return messageHeader.getId();
	}

	@Override
	public Author getAuthor() {
		return messageHeader.getAuthor();
	}

	@Override
	public String getContentType() {
		return messageHeader.getContentType();
	}

	@Override
	public long getTimestamp() {
		return messageHeader.getTimestamp();
	}

	@Override
	public boolean isLocal() {
		return messageHeader.isLocal();
	}

	@Override
	public boolean isRead() {
		return messageHeader.isRead();
	}

	@Override
	public Status getStatus() {
		switch (messageHeader.getStatus()) {
			case STORED:
				return Status.STORED;
			case SENT:
				return Status.SENT;
			case DELIVERED:
				return Status.DELIVERED;
			default:
				throw new IllegalStateException();
		}
	}
}
