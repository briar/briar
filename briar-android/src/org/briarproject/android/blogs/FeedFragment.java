package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.blogs.Blog;

import java.util.Collection;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.blogs.BlogActivity.BLOG_NAME;
import static org.briarproject.android.blogs.BlogActivity.IS_MY_BLOG;
import static org.briarproject.android.blogs.BlogActivity.REQUEST_WRITE_POST;

public class FeedFragment extends BaseFragment implements
		OnBlogPostClickListener, FeedController.OnBlogPostAddedListener {

	public final static String TAG = FeedFragment.class.getName();

	@Inject
	FeedController feedController;

	private BlogPostAdapter adapter;
	private LinearLayoutManager layoutManager;
	private BriarRecyclerView list;
	private Blog personalBlog = null;

	static FeedFragment newInstance() {
		FeedFragment f = new FeedFragment();

		Bundle args = new Bundle();
		f.setArguments(args);

		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);
		View v = inflater.inflate(R.layout.fragment_blog, container, false);

		adapter = new BlogPostAdapter(getActivity(), this);

		layoutManager = new LinearLayoutManager(getActivity());
		list = (BriarRecyclerView) v.findViewById(R.id.postList);
		list.setLayoutManager(layoutManager);
		list.setAdapter(adapter);
		list.setEmptyText(R.string.blogs_feed_empty_state);

		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		feedController.setOnBlogPostAddedListener(this);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// The BlogPostAddedEvent arrives when the controller is not listening
		if (requestCode == REQUEST_WRITE_POST && resultCode == RESULT_OK) {
			showSnackBar(R.string.blogs_blog_post_created);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		feedController.loadPersonalBlog(
				new UiResultHandler<Blog>(getActivity()) {
					@Override
					public void onResultUi(Blog b) {
						personalBlog = b;
					}
				});
	}

	@Override
	public void onResume() {
		super.onResume();
		list.startPeriodicUpdate();
		feedController.onResume();
		feedController.loadPosts(
				new UiResultHandler<Collection<BlogPostItem>>(getActivity()) {
					@Override
					public void onResultUi(Collection<BlogPostItem> posts) {
						if (posts == null) {
							// TODO show error?
						} else if (posts.isEmpty()) {
							list.showData();
						} else {
							adapter.addAll(posts);
						}
					}
				});
	}

	@Override
	public void onPause() {
		super.onPause();
		list.stopPeriodicUpdate();
		feedController.onPause();
		// TODO save list position in database/preferences?
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.blogs_feed_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (personalBlog == null) return false;
		ActivityOptionsCompat options =
				makeCustomAnimation(getActivity(), android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		switch (item.getItemId()) {
			case R.id.action_write_blog_post:
				Intent i1 =
						new Intent(getActivity(), WriteBlogPostActivity.class);
				i1.putExtra(GROUP_ID, personalBlog.getId().getBytes());
				i1.putExtra(BLOG_NAME, personalBlog.getName());
				startActivityForResult(i1, REQUEST_WRITE_POST,
						options.toBundle());
				return true;
			case R.id.action_rss_feeds_import:
				Intent i2 =
						new Intent(getActivity(), RssFeedImportActivity.class);
				i2.putExtra(GROUP_ID, personalBlog.getId().getBytes());
				startActivity(i2, options.toBundle());
				return true;
			case R.id.action_rss_feeds_manage:
				Intent i3 =
						new Intent(getActivity(), RssFeedManageActivity.class);
				i3.putExtra(GROUP_ID, personalBlog.getId().getBytes());
				startActivity(i3, options.toBundle());
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBlogPostAdded(final BlogPostItem post) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.add(post);
				showSnackBar(R.string.blogs_blog_post_received);
			}
		});
	}

	@Override
	public void onBlogPostClick(int position, BlogPostItem post) {
		byte[] groupId = post.getGroupId().getBytes();
		String name = getString(R.string.blogs_personal_blog,
				post.getAuthor().getName());
		boolean myBlog = personalBlog != null &&
				personalBlog.getId().equals(post.getGroupId());

		Intent i = new Intent(getActivity(), BlogActivity.class);
		i.putExtra(GROUP_ID, groupId);
		i.putExtra(BLOG_NAME, name);
		i.putExtra(IS_MY_BLOG, myBlog);
		ActivityOptionsCompat options =
				makeCustomAnimation(getActivity(),
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		startActivity(i, options.toBundle());
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void showSnackBar(int stringRes) {
		int firstVisible =
				layoutManager.findFirstCompletelyVisibleItemPosition();
		int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
		int count = adapter.getItemCount();
		boolean scroll = count > (lastVisible - firstVisible + 1);

		Snackbar s = Snackbar.make(list, stringRes, LENGTH_LONG);
		s.getView().setBackgroundResource(R.color.briar_primary);
		if (scroll) {
			OnClickListener onClick = new OnClickListener() {
				@Override
				public void onClick(View v) {
					list.smoothScrollToPosition(0);
				}
			};
			s.setActionTextColor(ContextCompat
					.getColor(getContext(),
							R.color.briar_button_positive));
			s.setAction(R.string.blogs_blog_post_scroll_to, onClick);
		}
		s.show();
	}

}
