package org.briarproject.briar.android.forum;

import org.briarproject.briar.android.threaded.ThreadItem;
import org.briarproject.briar.api.forum.ForumPostHeader;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class ForumPostItem extends ThreadItem {

	ForumPostItem(ForumPostHeader h) {
		super(h.getId(), h.getParentId(), h.getTimestamp(), h.getAuthor(),
				h.getAuthorInfo(), h.isRead());
	}

}
