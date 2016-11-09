package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.blogs.FeedController.FeedListener;
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
public class FeedPostPagerFragment extends BasePostPagerFragment
	implements FeedListener {

	private static final String TAG = FeedPostPagerFragment.class.getName();

	@Inject
	FeedController feedController;

	static FeedPostPagerFragment newInstance(MessageId postId) {
		FeedPostPagerFragment f = new FeedPostPagerFragment();

		Bundle args = new Bundle();
		args.putByteArray(POST_ID, postId.getBytes());
		f.setArguments(args);

		return f;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		feedController.setFeedListener(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onStart() {
		super.onStart();
		feedController.onStart();
	}

	@Override
	public void onStop() {
		super.onStop();
		feedController.onStop();
	}

	@Override
	Fragment createFragment(GroupId g, MessageId m) {
		return FeedPostFragment.newInstance(g, m);
	}

	@Override
	void loadBlogPosts(final MessageId select) {
		feedController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<BlogPostItem> posts) {
						onBlogPostsLoaded(select, posts);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
					}
				});
	}

	@Override
	void loadBlogPost(BlogPostHeader header) {
		feedController.loadBlogPost(header,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						onBlogPostLoaded(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
					}
				});
	}

	@Override
	public void onBlogAdded() {
		loadBlogPosts();
	}

	@Override
	public void onBlogRemoved() {
		loadBlogPosts();
	}
}
