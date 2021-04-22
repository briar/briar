package org.briarproject.briar.android.blog;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.material.snackbar.Snackbar;

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

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

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

		list = findViewById(R.id.feedList);
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
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		} else if (item.getItemId() == R.id.action_rss_feeds_import) {
			Intent i = new Intent(this, RssFeedImportActivity.class);
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
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
	public void onDeleteClick(Feed feed) {
		DialogInterface.OnClickListener okListener =
				(dialog, which) -> deleteFeed(feed);
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
		int revision = adapter.getRevision();
		runOnDbThread(() -> {
			try {
				displayFeeds(revision, feedManager.getFeeds());
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onLoadError();
			}
		});
	}

	private void displayFeeds(int revision, List<Feed> feeds) {
		runOnUiThreadUnlessDestroyed(() -> {
			if (revision == adapter.getRevision()) {
				adapter.incrementRevision();
				if (feeds.isEmpty()) list.showData();
				else adapter.addAll(feeds);
			} else {
				LOG.info("Concurrent update, reloading");
				loadFeeds();
			}
		});
	}

	private void deleteFeed(Feed feed) {
		runOnDbThread(() -> {
			try {
				feedManager.removeFeed(feed);
				onFeedDeleted(feed);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onDeleteError();
			}
		});
	}

	private void onLoadError() {
		runOnUiThreadUnlessDestroyed(() -> {
			list.setEmptyText(R.string.blogs_rss_feeds_manage_error);
			list.showData();
		});
	}

	private void onFeedDeleted(Feed feed) {
		runOnUiThreadUnlessDestroyed(() -> {
			adapter.incrementRevision();
			adapter.remove(feed);
		});
	}

	private void onDeleteError() {
		runOnUiThreadUnlessDestroyed(() -> Snackbar.make(list,
				R.string.blogs_rss_feeds_manage_delete_error,
				LENGTH_LONG).show());
	}
}

