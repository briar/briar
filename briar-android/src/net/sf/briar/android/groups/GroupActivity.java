package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.Rating.UNRATED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.AscendingHeaderComparator;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.GroupId;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class GroupActivity extends BriarActivity implements DatabaseListener,
OnClickListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(GroupActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private GroupId groupId = null;
	private String groupName = null;
	private GroupAdapter adapter = null;
	private ListView list = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		byte[] id = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(id == null) throw new IllegalStateException();
		groupId = new GroupId(id);
		groupName = i.getStringExtra("net.sf.briar.GROUP_NAME");
		if(groupName == null) throw new IllegalStateException();
		setTitle(groupName);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new GroupAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(CommonLayoutParams.MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		layout.addView(list);

		layout.addView(new HorizontalBorder(this));

		ImageButton composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		layout.addView(composeButton);

		setContentView(layout);

		// Listen for messages and groups being added or removed
		db.addListener(this);
		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		reloadMessageHeaders();
	}

	private void reloadMessageHeaders() {
		final DatabaseComponent db = this.db;
		final GroupId groupId = this.groupId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the message headers from the database
					Collection<GroupMessageHeader> headers =
							db.getMessageHeaders(groupId);
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + headers.size() + " headers");
					// Load the ratings for the authors
					Map<Author, Rating> ratings = new HashMap<Author, Rating>();
					for(GroupMessageHeader h : headers) {
						Author a = h.getAuthor();
						if(a != null && !ratings.containsKey(a))
							ratings.put(a, db.getRating(a.getId()));
					}
					ratings = Collections.unmodifiableMap(ratings);
					// Update the conversation
					updateConversation(headers, ratings);
				} catch(NoSuchSubscriptionException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Subscription removed");
					finishOnUiThread();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void updateConversation(
			final Collection<GroupMessageHeader> headers,
			final Map<Author, Rating> ratings) {
		runOnUiThread(new Runnable() {
			public void run() {
				List<GroupMessageHeader> sort =
						new ArrayList<GroupMessageHeader>(headers);
				Collections.sort(sort, AscendingHeaderComparator.INSTANCE);
				int firstUnread = -1;
				adapter.clear();
				for(GroupMessageHeader h : sort) {
					if(firstUnread == -1 && !h.isRead())
						firstUnread = adapter.getCount();
					Author a = h.getAuthor();
					if(a == null) adapter.add(new GroupItem(h, UNRATED));
					else adapter.add(new GroupItem(h, ratings.get(a)));
				}
				if(firstUnread == -1) list.setSelection(adapter.getCount() - 1);
				else list.setSelection(firstUnread);
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		unbindService(serviceConnection);
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof MessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			reloadMessageHeaders();
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message removed, reloading");
			reloadMessageHeaders();
		} else if(e instanceof RatingChangedEvent) {
			RatingChangedEvent r = (RatingChangedEvent) e;
			updateRating(r.getAuthorId(), r.getRating());
		} else if(e instanceof SubscriptionRemovedEvent) {
			SubscriptionRemovedEvent s = (SubscriptionRemovedEvent) e;
			if(s.getGroupId().equals(groupId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Subscription removed");
				finishOnUiThread();
			}
		}
	}

	private void updateRating(final AuthorId a, final Rating r) {
		runOnUiThread(new Runnable() {
			public void run() {
				boolean affected = false;
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					GroupItem item = adapter.getItem(i);
					Author author = item.getAuthor();
					if(author != null && author.getId().equals(a)) {
						item.setRating(r);
						affected = true;
					}
				}
				if(affected) list.invalidate();
			}
		});
	}

	public void onClick(View view) {
		Intent i = new Intent(this, WriteGroupMessageActivity.class);
		i.putExtra("net.sf.briar.GROUP_ID", groupId.getBytes());
		i.putExtra("net.sf.briar.GROUP_NAME", groupName);
		startActivity(i);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		showMessage(position);
	}

	private void showMessage(int position) {
		GroupItem item = adapter.getItem(position);
		Intent i = new Intent(this, ReadGroupMessageActivity.class);
		i.putExtra("net.sf.briar.GROUP_ID", groupId.getBytes());
		i.putExtra("net.sf.briar.GROUP_NAME", groupName);
		i.putExtra("net.sf.briar.MESSAGE_ID", item.getId().getBytes());
		Author author = item.getAuthor();
		if(author == null) {
			i.putExtra("net.sf.briar.ANONYMOUS", true);
		} else {
			i.putExtra("net.sf.briar.ANONYMOUS", false);
			i.putExtra("net.sf.briar.AUTHOR_ID", author.getId().getBytes());
			i.putExtra("net.sf.briar.AUTHOR_NAME", author.getName());
			i.putExtra("net.sf.briar.RATING", item.getRating().toString());
		}
		i.putExtra("net.sf.briar.CONTENT_TYPE", item.getContentType());
		i.putExtra("net.sf.briar.TIMESTAMP", item.getTimestamp());
		i.putExtra("net.sf.briar.FIRST", position == 0);
		i.putExtra("net.sf.briar.LAST", position == adapter.getCount() - 1);
		startActivityForResult(i, position);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		if(result == ReadGroupMessageActivity.RESULT_PREV) {
			int position = request - 1;
			if(position >= 0 && position < adapter.getCount())
				showMessage(position);
		} else if(result == ReadGroupMessageActivity.RESULT_NEXT) {
			int position = request + 1;
			if(position >= 0 && position < adapter.getCount())
				showMessage(position);
		}
	}
}
