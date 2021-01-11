package org.briarproject.briar.android.blog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.widget.LinkDialogFragment;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.util.UiUtils.MIN_DATE_RESOLUTION;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BasePostFragment extends BaseFragment {

	static final String POST_ID = "briar.POST_ID";

	private static final Logger LOG =
			getLogger(BasePostFragment.class.getName());

	private final Handler handler = new Handler(Looper.getMainLooper());

	protected MessageId postId;
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
		// retrieve MessageId of blog post from arguments
		byte[] p = requireArguments().getByteArray(POST_ID);
		if (p == null) throw new IllegalStateException("No post ID in args");
		postId = new MessageId(p);

		View view = inflater.inflate(R.layout.fragment_blog_post, container,
				false);
		progressBar = view.findViewById(R.id.progressBar);
		progressBar.setVisibility(VISIBLE);
		ui = new BlogPostViewHolder(view, true, new OnBlogPostClickListener() {
			@Override
			public void onBlogPostClick(BlogPostItem post) {
				// We're already there
			}

			@Override
			public void onAuthorClick(BlogPostItem post) {
				if (getContext() == null) return;
				Intent i = new Intent(getContext(), BlogActivity.class);
				i.putExtra(GROUP_ID, post.getGroupId().getBytes());
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				getContext().startActivity(i);
			}

			@Override
			public void onLinkClick(String url) {
				LinkDialogFragment f = LinkDialogFragment.newInstance(url);
				f.show(getParentFragmentManager(), f.getUniqueTag());
			}
		});
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
		refresher = () -> {
			LOG.info("Updating Content...");
			ui.updateDate(post.getTimestamp());
			handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
		};
		LOG.info("Adding Handler Callback");
		handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
	}

	private void stopPeriodicUpdate() {
		if (refresher != null) {
			LOG.info("Removing Handler Callback");
			handler.removeCallbacks(refresher);
		}
	}

}
