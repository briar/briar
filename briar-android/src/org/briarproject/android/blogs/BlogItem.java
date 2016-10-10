package org.briarproject.android.blogs;

import org.briarproject.api.blogs.Blog;

class BlogItem {

	private final Blog blog;
	private final boolean ours, removable;

	BlogItem(Blog blog, boolean ours, boolean removable) {
		this.blog = blog;
		this.ours = ours;
		this.removable = removable;
	}

	Blog getBlog() {
		return blog;
	}

	String getName() {
		return blog.getName();
	}

	boolean isOurs() {
		return ours;
	}

	boolean canBeRemoved() {
		return removable;
	}
}
