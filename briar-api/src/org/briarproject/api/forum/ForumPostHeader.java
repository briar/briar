package org.briarproject.api.forum;

import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

public interface ForumPostHeader {

	MessageId getId();

	Author getAuthor();

	Author.Status getAuthorStatus();

	String getContentType();

	long getTimestamp();

	boolean isRead();
}
