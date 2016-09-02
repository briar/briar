package org.briarproject.android.blogs;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.TrustIndicatorView;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

import java.util.logging.Logger;

import javax.inject.Inject;

import im.delight.android.identicons.IdenticonDrawable;

import static org.briarproject.android.util.AndroidUtils.MIN_RESOLUTION;

public class BlogPostFragment extends BaseFragment {

	public final static String TAG = BlogPostFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);
	private static final String BLOG_POST_ID = "briar.BLOG_POST_ID";

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

		View v = inflater.inflate(R.layout.fragment_blog_post, container,
				false);
		ui = new BlogPostViewHolder(v);
		return v;
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
						bind();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						getActivity().finish();
					}
				});
	}

	@Override
	public void onResume() {
		super.onResume();
		startPeriodicUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
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

	private void bind() {
		Author author = post.getAuthor();
		IdenticonDrawable d = new IdenticonDrawable(author.getId().getBytes());
		ui.avatar.setImageDrawable(d);
		ui.authorName.setText(author.getName());
		ui.trust.setTrustLevel(post.getAuthorStatus());
		Context ctx = getContext();
		if (ctx != null) {
			ui.date.setText(AndroidUtils.formatDate(ctx, post.getTimestamp()));
		}
		ui.body.setText(post.getBody());
	}

	private static class BlogPostViewHolder {

		private final ImageView avatar;
		private final TextView authorName;
		private final TrustIndicatorView trust;
		private final TextView date;
		private final TextView body;

		private BlogPostViewHolder(View v) {
			avatar = (ImageView) v.findViewById(R.id.avatar);
			authorName = (TextView) v.findViewById(R.id.authorName);
			trust = (TrustIndicatorView) v.findViewById(R.id.trustIndicator);
			date = (TextView) v.findViewById(R.id.date);
			body = (TextView) v.findViewById(R.id.body);
		}
	}

	private void startPeriodicUpdate() {
		refresher = new Runnable() {
			@Override
			public void run() {
				if (ui == null || post == null) return;
				LOG.info("Updating Content...");

				ui.date.setText(AndroidUtils
						.formatDate(getActivity(), post.getTimestamp()));
				ui.date.postDelayed(refresher, MIN_RESOLUTION);
			}
		};
		LOG.info("Adding Handler Callback");
		ui.date.postDelayed(refresher, MIN_RESOLUTION);
	}

	private void stopPeriodicUpdate() {
		if (refresher != null && ui != null) {
			LOG.info("Removing Handler Callback");
			ui.date.removeCallbacks(refresher);
		}
	}

}
