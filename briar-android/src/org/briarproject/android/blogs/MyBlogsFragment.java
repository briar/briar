package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class MyBlogsFragment extends BaseFragment {

	public final static String TAG = MyBlogsFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);
	private BriarRecyclerView list;
	private BlogListAdapter adapter;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	volatile BlogManager blogManager;

	@Inject
	public MyBlogsFragment() {
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		adapter = new BlogListAdapter(getActivity());

		list = (BriarRecyclerView) inflater
				.inflate(R.layout.fragment_blogs_my, container, false);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.blogs_my_blogs_empty_state));

		return list;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		listener.getActivityComponent().inject(this);
		// Starting from here, we can use injected objects
	}

	@Override
	public void onResume() {
		super.onResume();
		adapter.clear();
		loadBlogs();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.blogs_my_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_create_blog:
				Intent intent =
						new Intent(getContext(), CreateBlogActivity.class);
				ActivityOptionsCompat options =
						makeCustomAnimation(getActivity(),
								android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				ActivityCompat.startActivity(getActivity(), intent,
						options.toBundle());
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private void loadBlogs() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					// load blogs
					long now = System.currentTimeMillis();
					Collection<BlogListItem> blogs = new ArrayList<>();
					Collection<LocalAuthor> authors =
							identityManager.getLocalAuthors();
					LocalAuthor a = authors.iterator().next();
					for (Blog b : blogManager.getBlogs(a)) {
						try {
							Collection<BlogPostHeader> headers =
									blogManager.getPostHeaders(b.getId());
							blogs.add(new BlogListItem(b, headers, true));
						} catch (NoSuchGroupException e) {
							// Continue
						}
					}
					displayBlogs(blogs);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full blog load took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayBlogs(final Collection<BlogListItem> items) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (items.size() == 0) {
					list.showData();
				} else {
					adapter.addAll(items);
				}
			}
		});
	}

}
