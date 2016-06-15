package org.briarproject.api.forum;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

public class ForumPostHeader extends PostHeader
		implements MessageTree.MessageNode {

	public ForumPostHeader(MessageId id, MessageId parentId, long timestamp,
			Author author, Author.Status authorStatus, String contentType,
			boolean read) {
		super(id, parentId, timestamp, author, authorStatus, contentType, read);
	}

}
