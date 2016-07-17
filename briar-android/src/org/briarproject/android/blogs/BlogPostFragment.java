package org.briarproject.android.blogs;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.TrustIndicatorView;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import javax.inject.Inject;

import im.delight.android.identicons.IdenticonDrawable;

import static android.view.View.GONE;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.android.BriarActivity.GROUP_ID;

public class BlogPostFragment extends BaseFragment {

	public final static String TAG = BlogPostFragment.class.getName();

	private final static String BLOG_POST_ID = "briar.BLOG_NAME";

	private GroupId groupId;
	private MessageId postId;
	private BlogPostViewHolder ui;

	@Inject
	BlogController blogController;

	static BlogPostFragment newInstance(GroupId groupId, MessageId postId) {
		BlogPostFragment f = new BlogPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, groupId.getBytes());
		bundle.putByteArray(BLOG_POST_ID, postId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		byte[] b = getArguments().getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group found.");
		groupId = new GroupId(b);
		byte[] p = getArguments().getByteArray(BLOG_POST_ID);
		if (p == null) throw new IllegalStateException("No MessageId found.");
		postId = new MessageId(p);

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
		blogController.loadBlog(groupId, false,
				new UiResultHandler<Boolean>((Activity) listener) {
					@Override
					public void onResultUi(Boolean result) {
						listener.hideLoadingScreen();
						if (result) {
							BlogPostItem post =
									blogController.getBlogPost(postId);
							if (post != null) {
								bind(post);
							}
						} else {
							Toast.makeText(getActivity(),
									R.string.blogs_blog_post_failed_to_load,
									LENGTH_SHORT).show();
						}
					}
				});
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

	private void bind(BlogPostItem post) {
		Author author = post.getAuthor();
		IdenticonDrawable d = new IdenticonDrawable(author.getId().getBytes());
		ui.avatar.setImageDrawable(d);
		ui.authorName.setText(author.getName());
		ui.trust.setTrustLevel(post.getAuthorStatus());
		ui.date.setText(
				DateUtils.getRelativeTimeSpanString(post.getTimestamp()));

		if (post.getTitle() != null) {
			ui.title.setText(post.getTitle());
		} else {
			ui.title.setVisibility(GONE);
		}

		ui.body.setText(StringUtils.fromUtf8(post.getBody()));
	}

	private static class BlogPostViewHolder {
		private ImageView avatar;
		private TextView authorName;
		private TrustIndicatorView trust;
		private TextView date;
		private TextView title;
		private TextView body;

		BlogPostViewHolder(View v) {
			avatar = (ImageView) v.findViewById(R.id.avatar);
			authorName = (TextView) v.findViewById(R.id.authorName);
			trust = (TrustIndicatorView) v.findViewById(R.id.trustIndicator);
			date = (TextView) v.findViewById(R.id.date);
			title = (TextView) v.findViewById(R.id.title);
			body = (TextView) v.findViewById(R.id.body);
		}
	}

}
