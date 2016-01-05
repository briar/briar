package org.briarproject.android.forum;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.ElasticHorizontalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
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

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.ReadForumPostActivity.RESULT_PREV_NEXT;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class ForumActivity extends BriarActivity implements EventListener,
		OnClickListener, OnItemClickListener {

	private static final int REQUEST_READ = 2;
	private static final Logger LOG =
			Logger.getLogger(ForumActivity.class.getName());

	@Inject private AndroidNotificationManager notificationManager;
	private Map<MessageId, byte[]> bodyCache = new HashMap<MessageId, byte[]>();
	private TextView empty = null;
	private ForumAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton composeButton = null, shareButton = null;

	@Inject private ReferenceManager referenceManager;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ForumManager forumManager;
	@Inject private volatile EventBus eventBus;
	private volatile GroupId groupId = null;
	private volatile Forum forum = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra("briar.FORUM_NAME");
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

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		footer.addView(new ElasticHorizontalSpace(this));

		composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		footer.addView(composeButton);
		footer.addView(new ElasticHorizontalSpace(this));

		shareButton = new ImageButton(this);
		shareButton.setBackgroundResource(0);
		shareButton.setImageResource(R.drawable.social_share);
		shareButton.setOnClickListener(this);
		footer.addView(shareButton);
		footer.addView(new ElasticHorizontalSpace(this));
		layout.addView(footer);

		setContentView(layout);
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
				} catch (NoSuchSubscriptionException e) {
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
				} catch (NoSuchSubscriptionException e) {
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
		List<MessageId> unread = new ArrayList<MessageId>();
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
		} else if (e instanceof SubscriptionRemovedEvent) {
			SubscriptionRemovedEvent s = (SubscriptionRemovedEvent) e;
			if (s.getGroup().getId().equals(groupId)) {
				LOG.info("Subscription removed");
				finishOnUiThread();
			}
		}
	}

	public void onClick(View view) {
		if (view == composeButton) {
			Intent i = new Intent(this, WriteForumPostActivity.class);
			i.putExtra("briar.GROUP_ID", groupId.getBytes());
			i.putExtra("briar.FORUM_NAME", forum.getName());
			i.putExtra("briar.MIN_TIMESTAMP", getMinTimestampForNewPost());
			startActivity(i);
		} else if (view == shareButton) {
			Intent i = new Intent(this, ShareForumActivity.class);
			i.putExtra("briar.GROUP_ID", groupId.getBytes());
			i.putExtra("briar.FORUM_NAME", forum.getName());
			startActivity(i);
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
		i.putExtra("briar.GROUP_ID", groupId.getBytes());
		i.putExtra("briar.FORUM_NAME", forum.getName());
		i.putExtra("briar.MESSAGE_ID", header.getId().getBytes());
		Author author = header.getAuthor();
		if (author != null) i.putExtra("briar.AUTHOR_HANDLE",
				referenceManager.putReference(author, Author.class));
		i.putExtra("briar.AUTHOR_STATUS", header.getAuthorStatus().name());
		i.putExtra("briar.CONTENT_TYPE", header.getContentType());
		i.putExtra("briar.TIMESTAMP", header.getTimestamp());
		i.putExtra("briar.MIN_TIMESTAMP", getMinTimestampForNewPost());
		i.putExtra("briar.POSITION", position);
		startActivityForResult(i, REQUEST_READ);
	}
}
