package org.briarproject.android.forum;

import org.briarproject.android.threaded.ThreadItem;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

/* This class is not thread safe */
public class ForumEntry extends ThreadItem {

	ForumEntry(ForumPostHeader h, String text) {
		super(h.getId(), h.getParentId(), text, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus(), h.isRead());
	}

	public ForumEntry(MessageId messageId, MessageId parentId, String text,
			long timestamp, Author author, Status status) {
		super(messageId, parentId, text, timestamp, author, status, true);
	}

}
