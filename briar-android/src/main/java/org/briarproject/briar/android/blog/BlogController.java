package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

@NotNullByDefault
public interface BlogController extends BaseController {

	void setGroupId(GroupId g);

	void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler);

	void loadBlogPost(MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void loadBlog(ResultExceptionHandler<BlogItem, DbException> handler);

	void deleteBlog(ResultExceptionHandler<Void, DbException> handler);

}
