package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.threaded.ThreadItem;
import org.briarproject.briar.api.forum.ForumPostHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
class ForumPostItem extends ThreadItem {

	ForumPostItem(ForumPostHeader h, String text) {
		super(h.getId(), h.getParentId(), text, h.getTimestamp(), h.getAuthor(),
				h.getAuthorInfo(), h.isRead());
	}

	ForumPostItem(MessageId messageId, @Nullable MessageId parentId,
			String text, long timestamp, Author author, AuthorInfo authorInfo) {
		super(messageId, parentId, text, timestamp, author, authorInfo, true);
	}

}
