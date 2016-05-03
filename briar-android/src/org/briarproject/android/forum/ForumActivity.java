package org.briarproject.android.forum;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.ReadForumPostActivity.RESULT_PREV_NEXT;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class ForumActivity extends BriarActivity implements EventListener,
		OnItemClickListener {

	public static final String FORUM_NAME = "briar.FORUM_NAME";
	public static final String MIN_TIMESTAMP = "briar.MIN_TIMESTAMP";

	private static final int REQUEST_READ = 2;
	private static final Logger LOG =
			Logger.getLogger(ForumActivity.class.getName());

	@Inject protected AndroidNotificationManager notificationManager;
	private Map<MessageId, byte[]> bodyCache = new HashMap<>();
	private TextView empty = null;
	private ForumAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile ForumManager forumManager;
	@Inject protected volatile EventBus eventBus;
	private volatile GroupId groupId = null;
	private volatile Forum forum = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra(FORUM_NAME);
		if (forumName != null) setTitle(forumName);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		empty = new TextView(this);
		empty.setLayoutParams(MATCH_WRAP_1);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_forum_posts);
		empty.setVisibility(GONE);
		layout.addView(empty);

		adapter = new ForumAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setVisibility(GONE);
		layout.addView(list);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		setContentView(layout);
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		notificationManager.blockNotification(groupId);
		notificationManager.clearForumPostNotification(groupId);
		loadForum();
		loadHeaders();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.forum_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_forum_compose_post:
				Intent i = new Intent(this, WriteForumPostActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				i.putExtra(FORUM_NAME, forum.getName());
				i.putExtra(MIN_TIMESTAMP, getMinTimestampForNewPost());
				startActivity(i);
				return true;
			case R.id.action_forum_share:
				Intent i2 = new Intent(this, ShareForumActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				ActivityOptionsCompat options = ActivityOptionsCompat
						.makeCustomAnimation(this, android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				ActivityCompat.startActivity(this, i2, options.toBundle());
				return true;
			case R.id.action_forum_delete:
				showUnsubscribeDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void loadForum() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forum = forumManager.getForum(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading forum " + duration + " ms");
					displayForumName();
				} catch (NoSuchGroupException e) {
					finishOnUiThread();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayForumName() {
		runOnUiThread(new Runnable() {
			public void run() {
				setTitle(forum.getName());
			}
		});
	}

	private void loadHeaders() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<ForumPostHeader> headers =
							forumManager.getPostHeaders(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayHeaders(headers);
				} catch (NoSuchGroupException e) {
					finishOnUiThread();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayHeaders(final Collection<ForumPostHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				loading.setVisibility(GONE);
				adapter.clear();
				if (headers.isEmpty()) {
					empty.setVisibility(VISIBLE);
					list.setVisibility(GONE);
				} else {
					empty.setVisibility(GONE);
					list.setVisibility(VISIBLE);
					for (ForumPostHeader h : headers) {
						ForumItem item = new ForumItem(h);
						byte[] body = bodyCache.get(h.getId());
						if (body == null) loadPostBody(h);
						else item.setBody(body);
						adapter.add(item);
					}
					adapter.sort(ForumItemComparator.INSTANCE);
					// Scroll to the bottom
					list.setSelection(adapter.getCount() - 1);
				}
			}
		});
	}

	private void loadPostBody(final ForumPostHeader h) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = forumManager.getPostBody(h.getId());
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					displayPost(h.getId(), body);
				} catch (NoSuchMessageException e) {
					// The item will be removed when we get the event
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayPost(final MessageId m, final byte[] body) {
		runOnUiThread(new Runnable() {
			public void run() {
				bodyCache.put(m, body);
				int count = adapter.getCount();
				for (int i = 0; i < count; i++) {
					ForumItem item = adapter.getItem(i);
					if (item.getHeader().getId().equals(m)) {
						item.setBody(body);
						adapter.notifyDataSetChanged();
						// Scroll to the bottom
						list.setSelection(count - 1);
						return;
					}
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_READ && result == RESULT_PREV_NEXT) {
			int position = data.getIntExtra("briar.POSITION", -1);
			if (position >= 0 && position < adapter.getCount())
				displayPost(position);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
		notificationManager.unblockNotification(groupId);
		if (isFinishing()) markPostsRead();
	}

	private void markPostsRead() {
		List<MessageId> unread = new ArrayList<>();
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			ForumPostHeader h = adapter.getItem(i).getHeader();
			if (!h.isRead()) unread.add(h.getId());
		}
		if (unread.isEmpty()) return;
		if (LOG.isLoggable(INFO))
			LOG.info("Marking " + unread.size() + " posts read");
		markPostsRead(Collections.unmodifiableList(unread));
	}

	private void markPostsRead(final Collection<MessageId> unread) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (MessageId m : unread)
						forumManager.setReadFlag(m, true);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public void eventOccurred(Event e) {
		if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			if (m.isValid() && m.getMessage().getGroupId().equals(groupId)) {
				LOG.info("Message added, reloading");
				loadHeaders();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(groupId)) {
				LOG.info("Forum removed");
				finishOnUiThread();
			}
		}
	}

	private long getMinTimestampForNewPost() {
		// Don't use an earlier timestamp than the newest post
		long timestamp = 0;
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			long t = adapter.getItem(i).getHeader().getTimestamp();
			if (t > timestamp) timestamp = t;
		}
		return timestamp + 1;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		displayPost(position);
	}

	private void displayPost(int position) {
		ForumPostHeader header = adapter.getItem(position).getHeader();
		Intent i = new Intent(this, ReadForumPostActivity.class);
		i.putExtra(GROUP_ID, groupId.getBytes());
		i.putExtra(FORUM_NAME, forum.getName());
		i.putExtra("briar.MESSAGE_ID", header.getId().getBytes());
		Author author = header.getAuthor();
		if (author != null) {
			i.putExtra("briar.AUTHOR_NAME", author.getName());
			i.putExtra("briar.AUTHOR_ID", author.getId().getBytes());
		}
		i.putExtra("briar.AUTHOR_STATUS", header.getAuthorStatus().name());
		i.putExtra("briar.CONTENT_TYPE", header.getContentType());
		i.putExtra("briar.TIMESTAMP", header.getTimestamp());
		i.putExtra(MIN_TIMESTAMP, getMinTimestampForNewPost());
		i.putExtra("briar.POSITION", position);
		startActivityForResult(i, REQUEST_READ);
	}

	private void showUnsubscribeDialog() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						unsubscribe(forum);
						Toast.makeText(ForumActivity.this,
								R.string.forum_left_toast, LENGTH_SHORT)
								.show();
					}
				};
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ForumActivity.this,
						R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setPositiveButton(R.string.dialog_button_leave, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void unsubscribe(final Forum f) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.removeForum(f);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing forum took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

}
