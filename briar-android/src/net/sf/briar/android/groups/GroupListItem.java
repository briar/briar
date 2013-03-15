package net.sf.briar.android.groups;

import java.util.Collections;
import java.util.List;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;

class GroupListItem {

	private final Group group;
	private final String authorName, subject;
	private final long timestamp;
	private final int unread;

	GroupListItem(Group group, List<GroupMessageHeader> headers) {
		if(headers.isEmpty()) throw new IllegalArgumentException();
		this.group = group;
		Collections.sort(headers, DescendingHeaderComparator.INSTANCE);
		GroupMessageHeader newest = headers.get(0);
		Author a = newest.getAuthor();
		if(a == null) authorName = null;
		else authorName = a.getName();
		subject = newest.getSubject();
		timestamp = newest.getTimestamp();
		int unread = 0;
		for(GroupMessageHeader h : headers) if(!h.isRead()) unread++;
		this.unread = unread;
	}

	GroupId getGroupId() {
		return group.getId();
	}

	String getGroupName() {
		return group.getName();
	}

	String getAuthorName() {
		return authorName;
	}

	String getSubject() {
		return subject;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}
