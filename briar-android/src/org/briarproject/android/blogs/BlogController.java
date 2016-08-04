package org.briarproject.android.blogs;

import android.support.annotation.Nullable;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.SortedSet;

public interface BlogController extends ActivityLifecycleController {

	void loadBlog(GroupId groupId, boolean reload,
			ResultHandler<Boolean> resultHandler);

	SortedSet<BlogPostItem> getBlogPosts();

	@Nullable
	BlogPostItem getBlogPost(MessageId postId);

	@Nullable
	MessageId getBlogPostId(int position);

	void canDeleteBlog(GroupId groupId,
			ResultExceptionHandler<Boolean, DbException> resultHandler);

	void deleteBlog(ResultHandler<Boolean> resultHandler);

	interface BlogPostListener {
		void onBlogPostAdded(BlogPostItem post, boolean local);
	}

}
