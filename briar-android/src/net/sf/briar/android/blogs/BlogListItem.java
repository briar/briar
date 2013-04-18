package net.sf.briar.android.blogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.Author;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.messaging.Group;

class BlogListItem {

	static final BlogListItem MANAGE = new BlogListItem(null, false,
			Collections.<GroupMessageHeader>emptyList());

	private final Group group;
	private final boolean postable, empty;
	private final String authorName, contentType, subject;
	private final long timestamp;
	private final int unread;

	BlogListItem(Group group, boolean postable,
			Collection<GroupMessageHeader> headers) {
		this.group = group;
		this.postable = postable;
		empty = headers.isEmpty();
		if(empty) {
			authorName = null;
			contentType = null;
			subject = null;
			timestamp = 0;
			unread = 0;
		} else {
			List<GroupMessageHeader> list =
					new ArrayList<GroupMessageHeader>(headers);
			Collections.sort(list, DescendingHeaderComparator.INSTANCE);
			GroupMessageHeader newest = list.get(0);
			Author a = newest.getAuthor();
			if(a == null) authorName = null;
			else authorName = a.getName();
			contentType = newest.getContentType();
			subject = newest.getSubject();
			timestamp = newest.getTimestamp();
			int unread = 0;
			for(GroupMessageHeader h : list) if(!h.isRead()) unread++;
			this.unread = unread;
		}
	}

	Group getGroup() {
		return group;
	}

	boolean isPostable() {
		return postable;
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
