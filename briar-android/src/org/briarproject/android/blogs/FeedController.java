package org.briarproject.android.blogs;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.blogs.Blog;

import java.util.Collection;

public interface FeedController {

	void onResume();
	void onPause();

	void loadPosts(
			final UiResultHandler<Collection<BlogPostItem>> resultHandler);

	void loadPersonalBlog(final UiResultHandler<Blog> resultHandler);

	void setOnBlogPostAddedListener(OnBlogPostAddedListener listener);

	interface OnBlogPostAddedListener {
		void onBlogPostAdded(final BlogPostItem post);
	}

}
