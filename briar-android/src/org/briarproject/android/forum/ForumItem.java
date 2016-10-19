package org.briarproject.android.forum;

import org.briarproject.android.threaded.ThreadItem;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class ForumItem extends ThreadItem {

	ForumItem(ForumPostHeader h, String body) {
		super(h.getId(), h.getParentId(), body, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus(), h.isRead());
	}

	public ForumItem(MessageId messageId, MessageId parentId, String text,
			long timestamp, Author author, Status status) {
		super(messageId, parentId, text, timestamp, author, status, true);
	}

}
