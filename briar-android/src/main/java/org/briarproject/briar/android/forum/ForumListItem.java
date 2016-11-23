package org.briarproject.briar.android.forum;

import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumPostHeader;

// This class is NOT thread-safe
class ForumListItem {

	private final Forum forum;
	private int postCount, unread;
	private long timestamp;

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
