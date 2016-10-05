package org.briarproject.android.forum;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;

// This class is NOT thread-safe
class ForumListItem {

	private final Forum forum;
	private long postCount, unread, timestamp;

	ForumListItem(Forum forum, GroupCount count) {
		this.forum = forum;
		this.postCount = count.getMsgCount();
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	void addHeader(ForumPostHeader h) {
		postCount++;
		if (!h.isRead()) unread++;
		if (h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
	}

	Forum getForum() {
		return forum;
	}

	boolean isEmpty() {
		return postCount == 0;
	}

	long getPostCount() {
		return postCount;
	}

	long getTimestamp() {
		return timestamp;
	}

	long getUnreadCount() {
		return unread;
	}
}
