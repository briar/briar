package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.blogs.BlogController.BlogPostListener;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;

public class BlogActivity extends BriarActivity implements BlogPostListener,
		OnBlogPostClickListener, BaseFragmentListener {

	static final int REQUEST_WRITE_POST = 1;
	static final String BLOG_NAME = "briar.BLOG_NAME";
	static final String IS_MY_BLOG = "briar.IS_MY_BLOG";
	static final String IS_NEW_BLOG = "briar.IS_NEW_BLOG";

	private static final String BLOG_PAGER_ADAPTER = "briar.BLOG_PAGER_ADAPTER";

	private ProgressBar progressBar;
	private ViewPager pager;
	private BlogPagerAdapter blogPagerAdapter;
	private BlogPostPagerAdapter postPagerAdapter;
	private String blogName;
	private boolean myBlog, isNew;

	// Fields that are accessed from background threads must be volatile
	private volatile GroupId groupId = null;
	@Inject
	BlogController blogController;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group in intent.");
		groupId = new GroupId(b);

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
		if (state == null || state.getBoolean(BLOG_PAGER_ADAPTER, true)) {
			pager.setAdapter(blogPagerAdapter);
		} else {
			// this initializes and restores the postPagerAdapter
			loadBlogPosts();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		// remember which adapter we had active
		outState.putBoolean(BLOG_PAGER_ADAPTER,
				pager.getAdapter() == blogPagerAdapter);
	}

	@Override
	public void onBackPressed() {
		if (pager.getAdapter() == postPagerAdapter) {
			pager.setAdapter(blogPagerAdapter);
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
	public void onBlogPostClick(final int position) {
		loadBlogPosts(position, true);
	}

	private void loadBlogPosts() {
		loadBlogPosts(0, false);
	}

	private void loadBlogPosts(final int position, final boolean setItem) {
		showLoadingScreen();
		blogController.loadBlog(groupId, false,
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							Collection<BlogPostItem> posts =
									blogController.getBlogPosts();

							if (postPagerAdapter == null) {
								postPagerAdapter = new BlogPostPagerAdapter(
										getSupportFragmentManager(),
										posts.size());
							} else {
								postPagerAdapter.setSize(posts.size());
							}
							pager.setAdapter(postPagerAdapter);
							if (setItem) pager.setCurrentItem(position);
						} else {
							Toast.makeText(BlogActivity.this,
									R.string.blogs_blog_post_failed_to_load,
									LENGTH_SHORT).show();
						}
					}
				});
	}

	@Override
	public void onBlogPostAdded(final BlogPostItem post, final boolean local) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (blogPagerAdapter != null) {
					BlogFragment f = blogPagerAdapter.getFragment();
					if (f != null && f.isVisible()) {
						f.onBlogPostAdded(post, local);
					}
				}

				if (postPagerAdapter != null) {
					postPagerAdapter.onBlogPostAdded();
					postPagerAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {

		// The BlogPostAddedEvent arrives when the controller is not listening,
		// so we need to manually reload the blog posts :(
		if (requestCode == REQUEST_WRITE_POST && resultCode == RESULT_OK) {
			BlogFragment f = blogPagerAdapter.getFragment();
			if (f != null && f.isVisible()) {
				f.reload();
			}
		}
	}


	private class BlogPagerAdapter extends FragmentStatePagerAdapter {
		private BlogFragment fragment = null;

		BlogPagerAdapter(FragmentManager fm) {
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

		BlogFragment getFragment() {
			return fragment;
		}
	}

	private class BlogPostPagerAdapter extends FragmentStatePagerAdapter {
		private int size;

		BlogPostPagerAdapter(FragmentManager fm, int size) {
			super(fm);
			this.size = size;
		}

		@Override
		public int getCount() {
			return size;
		}

		@Override
		public Fragment getItem(int position) {
			MessageId postIdOfPos = blogController.getBlogPostId(position);
			return BlogPostFragment.newInstance(groupId, postIdOfPos);
		}

		void onBlogPostAdded() {
			size++;
		}

		void setSize(int size) {
			this.size = size;
		}
	}

}
