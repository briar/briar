package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.briarproject.R;
import org.briarproject.android.blogs.BaseController.OnBlogPostAddedListener;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.blogs.BasePostPagerFragment.BlogPostPagerAdapter.INVALID_POSITION;

abstract class BasePostPagerFragment extends BaseFragment
		implements OnBlogPostAddedListener {

	static final String POST_ID = "briar.POST_ID";

	private ViewPager pager;
	private ProgressBar progressBar;
	private BlogPostPagerAdapter postPagerAdapter;
	private MessageId postId;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle state) {

		Bundle args;
		if (state == null) args = getArguments();
		else args = state;
		byte[] p = args.getByteArray(POST_ID);
		if (p == null)
			throw new IllegalStateException("No post ID in args");
		postId = new MessageId(p);

		View v = inflater.inflate(R.layout.fragment_blog_post_pager, container,
				false);
		progressBar = (ProgressBar) v.findViewById(R.id.progressBar);
		progressBar.setVisibility(VISIBLE);

		pager = (ViewPager) v.findViewById(R.id.pager);
		postPagerAdapter = new BlogPostPagerAdapter(getChildFragmentManager());

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		if (postId == null) {
			MessageId selected = getSelectedPost();
			if (selected != null) loadBlogPosts(selected);
		} else {
			loadBlogPosts(postId);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		MessageId selected = getSelectedPost();
		if (selected != null)
			outState.putByteArray(POST_ID, selected.getBytes());
	}

	@Override
	public void onBlogPostAdded(BlogPostHeader header, boolean local) {
		loadBlogPost(header);
	}

	abstract void loadBlogPosts(final MessageId select);

	abstract void loadBlogPost(BlogPostHeader header);

	@UiThread
	protected void onBlogPostsLoaded(MessageId select,
			Collection<BlogPostItem> posts) {

		postId = null;
		postPagerAdapter.setPosts(posts);
		selectPost(select);
	}

	@UiThread
	protected void onBlogPostsLoadedException(DbException exception) {
		// TODO: Decide how to handle errors in the UI
		finish();
	}

	@Nullable
	private MessageId getSelectedPost() {
		if (postPagerAdapter.getCount() == 0) return null;
		int position = pager.getCurrentItem();
		return postPagerAdapter.getPost(position).getId();
	}

	private void selectPost(MessageId m) {
		int pos = postPagerAdapter.getPostPosition(m);
		if (pos != INVALID_POSITION) {
			progressBar.setVisibility(INVISIBLE);
			pager.setAdapter(postPagerAdapter);
			pager.setCurrentItem(pos);
		}
	}

	protected void addPost(BlogPostItem post) {
		MessageId selected = getSelectedPost();
		postPagerAdapter.addPost(post);
		if (selected != null) selectPost(selected);
	}

	@UiThread
	static class BlogPostPagerAdapter extends FragmentStatePagerAdapter {

		static final int INVALID_POSITION = -1;
		private final List<BlogPostItem> posts = new ArrayList<>();

		private BlogPostPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public int getCount() {
			return posts.size();
		}

		@Override
		public Fragment getItem(int position) {
			BlogPostItem post = posts.get(position);
			return FeedPostFragment.newInstance(post.getGroupId(), post.getId());
		}

		private BlogPostItem getPost(int position) {
			return posts.get(position);
		}

		private void setPosts(Collection<BlogPostItem> posts) {
			this.posts.clear();
			this.posts.addAll(posts);
			Collections.sort(this.posts);
			notifyDataSetChanged();
		}

		private void addPost(BlogPostItem post) {
			posts.add(post);
			Collections.sort(posts);
			notifyDataSetChanged();
		}

		private int getPostPosition(MessageId m) {
			int count = getCount();
			for (int i = 0; i < count; i++) {
				if (getPost(i).getId().equals(m)) {
					return i;
				}
			}
			return INVALID_POSITION;
		}
	}

	@Override
	public void onBlogRemoved() {
		finish();
	}
}
