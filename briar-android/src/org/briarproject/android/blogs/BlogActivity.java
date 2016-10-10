package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.api.sync.GroupId;

import javax.inject.Inject;

public class BlogActivity extends BriarActivity implements
		OnBlogPostClickListener, BaseFragmentListener {

	static final int REQUEST_WRITE_POST = 1;
	static final int REQUEST_SHARE = 2;
	static final String BLOG_NAME = "briar.BLOG_NAME";
	static final String IS_NEW_BLOG = "briar.IS_NEW_BLOG";

	@Inject
	BlogController blogController;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in intent");
		GroupId groupId = new GroupId(b);
		blogController.setGroupId(groupId);

		// Name of the blog
		String blogName = i.getStringExtra(BLOG_NAME);
		if (blogName != null) setTitle(blogName);

		// Was this blog just created?
		boolean isNew = i.getBooleanExtra(IS_NEW_BLOG, false);

		setContentView(R.layout.activity_fragment_container);

		if (state == null) {
			BlogFragment f = BlogFragment.newInstance(groupId, blogName, isNew);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.commit();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onBlogPostClick(BlogPostItem post) {
		BlogPostPagerFragment f = BlogPostPagerFragment.newInstance(post.getId());
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f, f.getUniqueTag())
				.addToBackStack(f.getUniqueTag())
				.commit();
	}

	@Override
	public void onFragmentCreated(String tag) {
	}
}
