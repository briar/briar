package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.blogs.BlogController.BlogPostListener;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class BlogActivity extends BriarActivity implements BlogPostListener,
		OnBlogPostClickListener, BaseFragmentListener {

	static final int REQUEST_WRITE_POST = 1;
	static final int REQUEST_SHARE = 2;
	static final String BLOG_NAME = "briar.BLOG_NAME";
	static final String IS_MY_BLOG = "briar.IS_MY_BLOG";
	static final String IS_NEW_BLOG = "briar.IS_NEW_BLOG";

	private static final String POST_ID = "briar.POST_ID";

	private GroupId groupId;
	private ProgressBar progressBar;
	private ViewPager pager;
	private BlogPagerAdapter blogPagerAdapter;
	private BlogPostPagerAdapter postPagerAdapter;
	private String blogName;
	private boolean myBlog, isNew;
	private MessageId savedPostId;

	@Inject
	BlogController blogController;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in intent");
		groupId = new GroupId(b);
		blogController.setGroupId(groupId);

		// Name of the Blog from Intent
		blogName = i.getStringExtra(BLOG_NAME);
		if (blogName != null) setTitle(blogName);

		// Is this our blog and was it just created?
		myBlog = i.getBooleanExtra(IS_MY_BLOG, false);
		isNew = i.getBooleanExtra(IS_NEW_BLOG, false);

		setContentView(R.layout.activity_blog);

		pager = (ViewPager) findViewById(R.id.pager);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		hideLoadingScreen();

		blogPagerAdapter = new BlogPagerAdapter(getSupportFragmentManager());
		postPagerAdapter = new BlogPostPagerAdapter(
				getSupportFragmentManager());

		if (state == null || state.getByteArray(POST_ID) == null) {
			pager.setAdapter(blogPagerAdapter);
			savedPostId = null;
		} else {
			// Adapter will be set in selectPostInPostPager()
			savedPostId = new MessageId(state.getByteArray(POST_ID));
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (savedPostId == null) {
			MessageId selected = getSelectedPostInPostPager();
			if (selected != null) loadBlogPosts(selected);
		} else {
			loadBlogPosts(savedPostId);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		MessageId selected = getSelectedPostInPostPager();
		if (selected != null)
			outState.putByteArray(POST_ID, selected.getBytes());
	}

	@Override
	public void onBackPressed() {
		if (pager.getAdapter() == postPagerAdapter) {
			pager.setAdapter(blogPagerAdapter);
			savedPostId = null;
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void showLoadingScreen(boolean isBlocking, int stringId) {
		progressBar.setVisibility(VISIBLE);
	}

	private void showLoadingScreen() {
		showLoadingScreen(false, 0);
	}

	@Override
	public void hideLoadingScreen() {
		progressBar.setVisibility(GONE);
	}

	@Override
	public void onFragmentCreated(String tag) {

	}

	@Override
	public void onBlogPostClick(BlogPostItem post) {
		loadBlogPosts(post.getId());
	}

	private void loadBlogPosts(final MessageId select) {
		showLoadingScreen();
		blogController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<BlogPostItem> posts) {
						hideLoadingScreen();
						savedPostId = null;
						postPagerAdapter.setPosts(posts);
						selectPostInPostPager(select);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Override
	public void onBlogPostAdded(BlogPostHeader header, boolean local) {
		if (pager.getAdapter() == postPagerAdapter) {
			loadBlogPost(header);
		} else {
			BlogFragment f = blogPagerAdapter.getFragment();
			if (f != null && f.isVisible()) f.onBlogPostAdded(header, local);
		}
	}

	private void loadBlogPost(BlogPostHeader header) {
		blogController.loadBlogPost(header,
				new UiResultExceptionHandler<BlogPostItem, DbException>(this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						addPostToPostPager(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Nullable
	private MessageId getSelectedPostInPostPager() {
		if (pager.getAdapter() != postPagerAdapter) return null;
		if (postPagerAdapter.getCount() == 0) return null;
		int position = pager.getCurrentItem();
		return postPagerAdapter.getPost(position).getId();
	}

	private void selectPostInPostPager(MessageId m) {
		int count = postPagerAdapter.getCount();
		for (int i = 0; i < count; i++) {
			if (postPagerAdapter.getPost(i).getId().equals(m)) {
				pager.setAdapter(postPagerAdapter);
				pager.setCurrentItem(i);
				return;
			}
		}
	}

	private void addPostToPostPager(BlogPostItem post) {
		MessageId selected = getSelectedPostInPostPager();
		postPagerAdapter.addPost(post);
		if (selected != null) selectPostInPostPager(selected);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// The BlogPostAddedEvent arrives when the controller is not listening,
		// so we need to manually reload the blog posts :(
		if (requestCode == REQUEST_WRITE_POST && resultCode == RESULT_OK) {
			if (pager.getAdapter() == postPagerAdapter) {
				MessageId selected = getSelectedPostInPostPager();
				if (selected != null) loadBlogPosts(selected);
			} else {
				BlogFragment f = blogPagerAdapter.getFragment();
				if (f != null && f.isVisible()) f.loadBlogPosts(true);
			}
		}
	}

	@UiThread
	private class BlogPagerAdapter extends FragmentStatePagerAdapter {

		private BlogFragment fragment = null;

		private BlogPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public int getCount() {
			return 1;
		}

		@Override
		public Fragment getItem(int position) {
			return BlogFragment.newInstance(groupId, blogName, myBlog, isNew);
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			// save a reference to the single fragment here for later
			fragment =
					(BlogFragment) super.instantiateItem(container, position);
			return fragment;
		}

		private BlogFragment getFragment() {
			return fragment;
		}
	}

	@UiThread
	private static class BlogPostPagerAdapter
			extends FragmentStatePagerAdapter {

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
			return BlogPostFragment.newInstance(posts.get(position).getId());
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
	}

}
