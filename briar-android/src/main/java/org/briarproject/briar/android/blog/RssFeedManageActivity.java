package org.briarproject.briar.android.blog;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.blog.RssFeedAdapter.RssFeedListener;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.FeedManager;

import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

public class RssFeedManageActivity extends BriarActivity
		implements RssFeedListener {

	private static final Logger LOG =
			Logger.getLogger(RssFeedManageActivity.class.getName());

	private BriarRecyclerView list;
	private RssFeedAdapter adapter;

	@Inject
	@SuppressWarnings("WeakerAccess")
	volatile FeedManager feedManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_rss_feed_manage);

		adapter = new RssFeedAdapter(this, this);

		list = (BriarRecyclerView) findViewById(R.id.feedList);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	public void onStart() {
		super.onStart();
		loadFeeds();
	}

	@Override
	public void onStop() {
		super.onStop();
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.rss_feed_manage_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_rss_feeds_import:
				Intent i = new Intent(this, RssFeedImportActivity.class);
				startActivity(i);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onFeedClick(Feed feed) {
		Intent i = new Intent(this, BlogActivity.class);
		i.putExtra(GROUP_ID, feed.getBlogId().getBytes());
		i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
	}

	@Override
	public void onDeleteClick(final Feed feed) {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						deleteFeed(feed);
					}
				};
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.blogs_rss_remove_feed));
		builder.setMessage(
				getString(R.string.blogs_rss_remove_feed_dialog_message));
		builder.setPositiveButton(R.string.cancel, null);
		builder.setNegativeButton(R.string.blogs_rss_remove_feed_ok,
				okListener);
		builder.show();
	}

	private void loadFeeds() {
		final int revision = adapter.getRevision();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					displayFeeds(revision, feedManager.getFeeds());
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					onLoadError();
				}
			}
		});
	}

	private void displayFeeds(final int revision, final List<Feed> feeds) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (revision == adapter.getRevision()) {
					adapter.incrementRevision();
					if (feeds.isEmpty()) list.showData();
					else adapter.addAll(feeds);
				} else {
					LOG.info("Concurrent update, reloading");
					loadFeeds();
				}
			}
		});
	}

	private void deleteFeed(final Feed feed) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					feedManager.removeFeed(feed);
					onFeedDeleted(feed);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					onDeleteError();
				}
			}
		});
	}

	private void onLoadError() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				list.setEmptyText(R.string.blogs_rss_feeds_manage_error);
				list.showData();
			}
		});
	}

	private void onFeedDeleted(final Feed feed) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.incrementRevision();
				adapter.remove(feed);
			}
		});
	}

	private void onDeleteError() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				Snackbar.make(list,
						R.string.blogs_rss_feeds_manage_delete_error,
						LENGTH_LONG).show();
			}
		});
	}
}

