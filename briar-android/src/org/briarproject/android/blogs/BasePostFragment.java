package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;

import java.util.logging.Logger;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.util.AndroidUtils.MIN_RESOLUTION;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BasePostFragment extends BaseFragment {

	static final String POST_ID = "briar.POST_ID";

	private static final Logger LOG =
			Logger.getLogger(BasePostFragment.class.getName());

	private View view;
	private ProgressBar progressBar;
	private BlogPostViewHolder ui;
	private BlogPostItem post;
	private Runnable refresher;

	@CallSuper
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.fragment_blog_post, container,
				false);
		progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
		progressBar.setVisibility(VISIBLE);
		ui = new BlogPostViewHolder(view);
		return view;
	}

	@CallSuper
	@Override
	public void onStart() {
		super.onStart();
		startPeriodicUpdate();
	}

	@CallSuper
	@Override
	public void onStop() {
		super.onStop();
		stopPeriodicUpdate();
	}

	@UiThread
	protected void onBlogPostLoaded(BlogPostItem post) {
		progressBar.setVisibility(INVISIBLE);
		this.post = post;
		ui.bindItem(post);
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
