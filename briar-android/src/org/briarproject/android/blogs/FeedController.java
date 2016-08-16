package org.briarproject.android.blogs;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.db.DbException;

import java.util.Collection;

public interface FeedController extends BaseController {

	void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadPersonalBlog(ResultHandler<Blog> resultHandler);

}
