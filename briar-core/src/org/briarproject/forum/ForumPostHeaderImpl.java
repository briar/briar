package org.briarproject.forum;

import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

// Temporary facade during sync protocol refactoring
class ForumPostHeaderImpl implements ForumPostHeader {

	private final MessageHeader messageHeader;

	ForumPostHeaderImpl(MessageHeader messageHeader) {
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
	public Author.Status getAuthorStatus() {
		return messageHeader.getAuthorStatus();
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
	public boolean isRead() {
		return messageHeader.isRead();
	}
}
