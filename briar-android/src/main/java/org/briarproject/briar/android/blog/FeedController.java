package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.blog.Blog;

import java.util.Collection;

import androidx.annotation.UiThread;

@NotNullByDefault
public interface FeedController extends BaseController {

	void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadPersonalBlog(ResultExceptionHandler<Blog, DbException> handler);

	@UiThread
	void setFeedListener(FeedListener listener);

	@NotNullByDefault
	interface FeedListener extends BlogListener {

		@UiThread
		void onBlogAdded();
	}
}
