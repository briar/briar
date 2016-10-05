package org.briarproject.android.forum;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;

import java.util.Collection;

// This class is NOT thread-safe
class ForumListItem {

	private final Forum forum;
	private final boolean empty;
	private final int postCount;
	private final long timestamp;
	private final int unread;

	ForumListItem(Forum forum, Collection<ForumPostHeader> headers) {
		this.forum = forum;
		empty = headers.isEmpty();
		if (empty) {
			postCount = 0;
			timestamp = 0;
			unread = 0;
		} else {
			ForumPostHeader newest = null;
			long timestamp = -1;
			int unread = 0;
			for (ForumPostHeader h : headers) {
				if (h.getTimestamp() > timestamp) {
					timestamp = h.getTimestamp();
					newest = h;
				}
				if (!h.isRead()) unread++;
			}
			this.postCount = headers.size();
			this.timestamp = newest.getTimestamp();
			this.unread = unread;
		}
	}

	Forum getForum() {
		return forum;
	}

	boolean isEmpty() {
		return empty;
	}

	int getPostCount() {
		return postCount;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}
