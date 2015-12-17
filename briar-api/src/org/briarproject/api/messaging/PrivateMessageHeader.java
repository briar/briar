package org.briarproject.api.messaging;

import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

public interface PrivateMessageHeader {

	enum Status { STORED, SENT, DELIVERED }

	MessageId getId();

	Author getAuthor();

	String getContentType();

	long getTimestamp();

	boolean isLocal();

	boolean isRead();

	Status getStatus();
}
