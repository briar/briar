package org.briarproject.android.blogs;

import android.os.Bundle;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.android.blogs.BlogActivity.POST_ID;

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
	BaseController getController() {
		return feedController;
	}

	void loadBlogPosts(final MessageId select) {
		feedController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						getActivity()) {
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

}
