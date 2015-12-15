package org.briarproject.android.forum;

import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.MessageHeader;

import java.util.Collection;

class ForumListItem {

	private final Group group;
	private final boolean empty;
	private final long timestamp;
	private final int unread;

	ForumListItem(Group group, Collection<MessageHeader> headers) {
		this.group = group;
		empty = headers.isEmpty();
		if (empty) {
			timestamp = 0;
			unread = 0;
		} else {
			MessageHeader newest = null;
			long timestamp = -1;
			int unread = 0;
			for (MessageHeader h : headers) {
				if (h.getTimestamp() > timestamp) {
					timestamp = h.getTimestamp();
					newest = h;
				}
				if (!h.isRead()) unread++;
			}
			this.timestamp = newest.getTimestamp();
			this.unread = unread;
		}
	}

	Group getGroup() {
		return group;
	}

	boolean isEmpty() {
		return empty;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}
