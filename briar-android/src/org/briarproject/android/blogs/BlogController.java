package org.briarproject.android.blogs;

import android.support.annotation.Nullable;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.TreeSet;

public interface BlogController extends ActivityLifecycleController {

	void loadBlog(final GroupId groupId, final boolean reload,
			final UiResultHandler<Boolean> resultHandler);

	TreeSet<BlogPostItem> getBlogPosts();

	@Nullable
	BlogPostItem getBlogPost(MessageId postId);

	@Nullable
	MessageId getBlogPostId(int position);

	void deleteBlog(final UiResultHandler<Boolean> resultHandler);

	interface BlogPostListener {
		void onBlogPostAdded(final BlogPostItem post, final boolean local);
	}

}
