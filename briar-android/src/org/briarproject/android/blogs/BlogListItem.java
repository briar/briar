package org.briarproject.android.blogs;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogPostHeader;

import java.util.Collection;

class BlogListItem {

	private final Blog blog;
	private final int postCount;
	private final long timestamp;
	private final int unread;

	BlogListItem(Blog blog, Collection<BlogPostHeader> headers) {
		this.blog = blog;
		if (headers.isEmpty()) {
			postCount = 0;
			timestamp = 0;
			unread = 0;
		} else {
			BlogPostHeader newest = null;
			long timestamp = -1;
			int unread = 0;
			for (BlogPostHeader h : headers) {
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

	Blog getBlog() {
		return blog;
	}

	String getName() {
		return blog.getName();
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
