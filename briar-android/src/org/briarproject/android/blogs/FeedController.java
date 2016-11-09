package org.briarproject.android.blogs;

import android.support.annotation.UiThread;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;

import java.util.Collection;

public interface FeedController extends BaseController {

	void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadPersonalBlog(ResultExceptionHandler<Blog, DbException> handler);

	void setFeedListener(FeedListener listener);

	@NotNullByDefault
	interface FeedListener extends BlogListener {

		@UiThread
		void onBlogAdded();
	}
}
