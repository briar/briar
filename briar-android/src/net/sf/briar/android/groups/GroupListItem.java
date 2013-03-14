package net.sf.briar.android.groups;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;

// This class is not thread-safe
class GroupListItem {

	private final Set<MessageId> messageIds = new HashSet<MessageId>();
	private final Group group;
	private String authorName, subject;
	private long timestamp;
	private int unread;

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
		unread = 0;
		for(GroupMessageHeader h : headers) {
			if(!h.isRead()) unread++;
			if(!messageIds.add(h.getId())) throw new IllegalArgumentException();
		}
	}

	GroupListItem(Group group, Message first, boolean incoming) {
		this.group = group;
		Author a = first.getAuthor();
		if(a == null) authorName = null;
		else authorName = a.getName();
		subject = first.getSubject();
		timestamp = first.getTimestamp();
		unread = incoming ? 1 : 0;
		messageIds.add(first.getId());
	}

	boolean add(Message m, boolean incoming) {
		if(!messageIds.add(m.getId())) return false;
		if(m.getTimestamp() > timestamp) {
			// The added message is the newest
			Author a = m.getAuthor();
			if(a == null) authorName = null;
			else authorName = a.getName();
			subject = m.getSubject();
			timestamp = m.getTimestamp();
		}
		if(incoming) unread++;
		return true;
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
