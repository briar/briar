package org.briarproject.android.blogs;

import android.support.annotation.UiThread;

import org.briarproject.api.blogs.BlogCommentHeader;
import org.briarproject.api.blogs.BlogPostHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@UiThread
class BlogCommentItem extends BlogPostItem {

	private final BlogPostHeader postHeader;
	private final List<BlogCommentHeader> comments = new ArrayList<>();

	BlogCommentItem(BlogCommentHeader header) {
		super(header, null);
		postHeader = collectComments(header);
		Collections.sort(comments, new BlogCommentComparator());
	}

	private BlogPostHeader collectComments(BlogPostHeader header) {
		if (header instanceof BlogCommentHeader) {
			BlogCommentHeader comment = (BlogCommentHeader) header;
			if (comment.getComment() != null)
				comments.add(comment);
			return collectComments(comment.getParent());
		} else {
			return header;
		}
	}

	public void setBody(String body) {
		this.body = body;
	}

	@Override
	public BlogCommentHeader getHeader() {
		return (BlogCommentHeader) super.getHeader();
	}

	@Override
	BlogPostHeader getPostHeader() {
		return postHeader;
	}

	List<BlogCommentHeader> getComments() {
		return comments;
	}

	private static class BlogCommentComparator
			implements Comparator<BlogCommentHeader> {
		@Override
		public int compare(org.briarproject.api.blogs.BlogCommentHeader h1,
				org.briarproject.api.blogs.BlogCommentHeader h2) {
			// re-use same comparator used for blog posts, but reverse it
			return BlogCommentItem.compare(h2, h1);
		}
	}
}
