package net.sf.briar.android.groups;

import java.util.Collection;
import java.util.Collections;

import net.sf.briar.api.Author;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.messaging.Group;

class GroupListItem {

	static final GroupListItem MANAGE = new GroupListItem(null,
			Collections.<MessageHeader>emptyList());

	private final Group group;
	private final boolean empty;
	private final String authorName, contentType;
	private final long timestamp;
	private final int unread;

	GroupListItem(Group group, Collection<MessageHeader> headers) {
		this.group = group;
		empty = headers.isEmpty();
		if(empty) {
			authorName = null;
			contentType = null;
			timestamp = 0;
			unread = 0;
		} else {
			MessageHeader newest = null;
			long timestamp = 0;
			int unread = 0;
			for(MessageHeader h : headers) {
				if(h.getTimestamp() > timestamp) {
					timestamp = h.getTimestamp();
					newest = h;
				}
				if(!h.isRead()) unread++;
			}
			Author a = newest.getAuthor();
			if(a == null) authorName = null;
			else authorName = a.getName();
			contentType = newest.getContentType();
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

	String getAuthorName() {
		return authorName;
	}

	String getContentType() {
		return contentType;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}
