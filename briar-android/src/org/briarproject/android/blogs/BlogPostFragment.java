package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.android.util.AndroidUtils.MIN_RESOLUTION;

public class BlogPostFragment extends BaseFragment {

	public final static String TAG = BlogPostFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);
	private static final String BLOG_POST_ID = "briar.BLOG_POST_ID";

	private View view;
	private MessageId postId;
	private BlogPostViewHolder ui;
	private BlogPostItem post;
	private Runnable refresher;

	@Inject
	BlogController blogController;

	static BlogPostFragment newInstance(MessageId postId) {
		BlogPostFragment f = new BlogPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(BLOG_POST_ID, postId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		setHasOptionsMenu(true);

		byte[] b = getArguments().getByteArray(BLOG_POST_ID);
		if (b == null) throw new IllegalStateException("No post ID in args");
		postId = new MessageId(b);

		view = inflater.inflate(R.layout.fragment_blog_post, container,
				false);
		ui = new BlogPostViewHolder(view);
		return view;
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
						listener.hideLoadingScreen();
						BlogPostFragment.this.post = post;
						ui.bindItem(post);
						startPeriodicUpdate();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Override
	public void onStop() {
		super.onStop();
		stopPeriodicUpdate();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				getActivity().onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void startPeriodicUpdate() {
		refresher = new Runnable() {
			@Override
			public void run() {
				if (ui == null) return;
				LOG.info("Updating Content...");

				ui.updateDate(post.getTimestamp());
				view.postDelayed(refresher, MIN_RESOLUTION);
			}
		};
		LOG.info("Adding Handler Callback");
		view.postDelayed(refresher, MIN_RESOLUTION);
	}

	private void stopPeriodicUpdate() {
		if (refresher != null && ui != null) {
			LOG.info("Removing Handler Callback");
			view.removeCallbacks(refresher);
		}
	}

}
