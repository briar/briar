package org.briarproject.android.blogs;

import android.support.annotation.UiThread;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface BlogController extends BaseController {

	void setGroupId(GroupId g);

	void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadBlogPost(MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void loadBlog(ResultExceptionHandler<BlogItem, DbException> handler);

	void deleteBlog(ResultExceptionHandler<Void, DbException> handler);

}
