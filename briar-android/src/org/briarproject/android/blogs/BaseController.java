package org.briarproject.android.blogs;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.android.controller.handler.ExceptionHandler;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

interface BaseController {

	@UiThread
	void onStart();

	@UiThread
	void onStop();

	void loadBlogPosts(GroupId g,
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadBlogPost(BlogPostHeader header,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void loadBlogPost(GroupId g, MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void repeatPost(BlogPostItem item, @Nullable String comment,
			ExceptionHandler<DbException> handler);

	void setBlogListener(BlogListener listener);

	@NotNullByDefault
	interface BlogListener extends DestroyableContext {

		@UiThread
		void onBlogPostAdded(BlogPostHeader header, boolean local);

		@UiThread
		void onBlogRemoved();
	}

}
