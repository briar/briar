package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

import javax.inject.Inject;

import static org.briarproject.android.blogs.BasePostPagerFragment.POST_ID;

public class BlogPostFragment extends BasePostFragment {

	public final static String TAG = BlogPostFragment.class.getName();

	private MessageId postId;

	@Inject
	BlogController blogController;

	static BlogPostFragment newInstance(MessageId postId) {
		BlogPostFragment f = new BlogPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(POST_ID, postId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		Bundle args = getArguments();
		byte[] p = args.getByteArray(POST_ID);
		if (p == null) throw new IllegalStateException("No post ID in args");
		postId = new MessageId(p);

		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		blogController.loadBlogPost(postId,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						getActivity()) {
					@Override
					public void onResultUi(BlogPostItem post) {
						onBlogPostLoaded(post);
					}
					@Override
					public void onExceptionUi(DbException exception) {
						onBlogPostLoadException(exception);
					}
				});
	}
}
