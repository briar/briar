package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.blog.BlogPostHeader;

import java.util.List;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;

@NotNullByDefault
interface BaseController {

	@UiThread
	void onStart();

	@UiThread
	void onStop();

	void loadBlogPosts(GroupId g,
			ResultExceptionHandler<List<BlogPostItem>, DbException> handler);

	void loadBlogPost(BlogPostHeader header,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void loadBlogPost(GroupId g, MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void repeatPost(BlogPostItem item, @Nullable String comment,
			ExceptionHandler<DbException> handler);

	@NotNullByDefault
	interface BlogListener {

		@UiThread
		void onBlogPostAdded(BlogPostHeader header, boolean local);

		@UiThread
		void onBlogRemoved();
	}

}
