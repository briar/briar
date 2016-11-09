package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

import javax.inject.Inject;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BlogPostPagerFragment extends BasePostPagerFragment {

	private static final String TAG = BlogPostPagerFragment.class.getName();

	@Inject
	BlogController blogController;

	static BlogPostPagerFragment newInstance(MessageId postId) {
		BlogPostPagerFragment f = new BlogPostPagerFragment();

		Bundle args = new Bundle();
		args.putByteArray(POST_ID, postId.getBytes());
		f.setArguments(args);

		return f;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		blogController.setBlogListener(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	Fragment createFragment(final GroupId g, final MessageId m) {
		return BlogPostFragment.newInstance(m);
	}

	@Override
	void loadBlogPosts(final MessageId select) {
		blogController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<BlogPostItem> posts) {
						onBlogPostsLoaded(select, posts);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Override
	void loadBlogPost(BlogPostHeader header) {
		blogController.loadBlogPost(header,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						onBlogPostLoaded(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Override
	public void onBlogRemoved() {
		finish();
	}
}
