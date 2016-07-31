package org.briarproject.android.blogs;

import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.blogs.Blog;

import java.util.Collection;

public interface FeedController {

	void onResume();

	void onPause();

	void loadPosts(ResultHandler<Collection<BlogPostItem>> resultHandler);

	void loadPersonalBlog(ResultHandler<Blog> resultHandler);

	void setOnBlogPostAddedListener(OnBlogPostAddedListener listener);

	interface OnBlogPostAddedListener {
		void onBlogPostAdded(final BlogPostItem post);
	}

}
