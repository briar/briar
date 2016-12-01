package org.briarproject.briar.android.blog;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.DestroyableContext;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.blog.BlogPostHeader;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
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
