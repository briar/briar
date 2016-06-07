package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class BlogActivity extends BriarActivity {

	static final String BLOG_NAME = "briar.BLOG_NAME";
	static final String IS_MY_BLOG = "briar.IS_MY_BLOG";
	static final String IS_NEW_BLOG = "briar.IS_NEW_BLOG";
	private static final int WRITE_POST = 1;

	private static final Logger LOG =
			Logger.getLogger(BlogActivity.class.getName());

	private BlogPostAdapter adapter;
	private BriarRecyclerView list;
	private String blogName;
	private boolean myBlog;

	// Fields that are accessed from background threads must be volatile
	private volatile GroupId groupId = null;
	private volatile boolean scrollToTop = false;
	@Inject
	volatile BlogManager blogManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_blog);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group in intent.");
		groupId = new GroupId(b);
		blogName = i.getStringExtra(BLOG_NAME);
		if (blogName != null) setTitle(blogName);
		myBlog = i.getBooleanExtra(IS_MY_BLOG, false);

		adapter = new BlogPostAdapter(this, blogName);
		list = (BriarRecyclerView) this.findViewById(R.id.postList);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		if (myBlog) {
			list.setEmptyText(
					getString(R.string.blogs_my_blogs_blog_empty_state));
		} else {
			list.setEmptyText(getString(R.string.blogs_other_blog_empty_state));
		}

		// show snackbar if this blog was just created
		boolean isNew = i.getBooleanExtra(IS_NEW_BLOG, false);
		if (isNew) {
			Snackbar s = Snackbar.make(list, R.string.blogs_my_blogs_created,
					LENGTH_LONG);
			s.getView().setBackgroundResource(R.color.briar_primary);
			s.show();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		loadBlogPosts();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (myBlog) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.blogs_my_blog_actions, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_write_blog_post:
/*				Intent i = new Intent(this, WriteBlogPostActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				i.putExtra(BLOG_NAME, blogName);
				startActivityForResult(i, WRITE_POST);
*/				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == WRITE_POST && resultCode == RESULT_OK) {
			scrollToTop = true;
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void loadBlogPosts() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					// load blog posts
					long now = System.currentTimeMillis();
					Collection<BlogPostItem> posts = new ArrayList<>();
					try {
						Collection<BlogPostHeader> header =
								blogManager.getPostHeaders(groupId);
						for (BlogPostHeader h : header) {
							posts.add(new BlogPostItem(h));
						}
					} catch (NoSuchGroupException e) {
						// Continue
					}
					displayBlogPosts(posts);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Post header load took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayBlogPosts(final Collection<BlogPostItem> items) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (items.size() == 0) {
					list.showData();
				} else {
					adapter.addAll(items);
					if (scrollToTop) list.scrollToPosition(0);
				}
				scrollToTop = false;
			}
		});
	}

	// TODO listen to events and add new blog posts as they come in

}
