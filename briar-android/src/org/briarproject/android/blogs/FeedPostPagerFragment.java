package org.briarproject.android.blogs;

import android.os.Bundle;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

import javax.inject.Inject;

public class FeedPostPagerFragment extends BasePostPagerFragment {

	public final static String TAG = FeedPostPagerFragment.class.getName();

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
		feedController.setOnBlogPostAddedListener(this);
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
						onBlogPostsLoadedException(exception);
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
						addPost(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}
}
