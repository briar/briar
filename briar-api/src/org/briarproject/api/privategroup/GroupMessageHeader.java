package org.briarproject.api.privategroup;

import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

public class GroupMessageHeader extends PostHeader {

	public GroupMessageHeader(MessageId id, MessageId parentId, long timestamp,
			Author author, Author.Status authorStatus, boolean read) {
		super(id, parentId, timestamp, author, authorStatus, read);
	}

}
