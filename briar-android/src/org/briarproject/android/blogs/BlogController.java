package org.briarproject.android.blogs;

import android.support.annotation.Nullable;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.TreeSet;

public interface BlogController extends ActivityLifecycleController {

	void loadBlog(final GroupId groupId, final boolean reload,
			final ResultHandler<Boolean> resultHandler);

	TreeSet<BlogPostItem> getBlogPosts();

	@Nullable
	BlogPostItem getBlogPost(MessageId postId);

	@Nullable
	MessageId getBlogPostId(int position);

	void deleteBlog(final ResultHandler<Boolean> resultHandler);

	interface BlogPostListener {
		void onBlogPostAdded(final BlogPostItem post, final boolean local);
	}

}
