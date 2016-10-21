package org.briarproject.android.privategroup.conversation;

import org.briarproject.android.threaded.ThreadItem;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.sync.MessageId;

class GroupMessageItem extends ThreadItem {

	private GroupMessageItem(MessageId messageId, MessageId parentId,
			String text, long timestamp, Author author, Status status,
			boolean isRead) {
		super(messageId, parentId, text, timestamp, author, status, isRead);
	}

	GroupMessageItem(GroupMessageHeader h, String text) {
		this(h.getId(), h.getParentId(), text, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus(), h.isRead());
	}

}
