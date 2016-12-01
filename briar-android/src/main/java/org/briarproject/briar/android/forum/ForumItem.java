package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.threaded.ThreadItem;
import org.briarproject.briar.api.forum.ForumPostHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
class ForumItem extends ThreadItem {

	ForumItem(ForumPostHeader h, String body) {
		super(h.getId(), h.getParentId(), body, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus(), h.isRead());
	}

	ForumItem(MessageId messageId, @Nullable MessageId parentId, String text,
			long timestamp, Author author, Status status) {
		super(messageId, parentId, text, timestamp, author, status, true);
	}

}
