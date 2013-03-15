package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.Rating.UNRATED;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
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
	private final Map<AuthorId, Rating> ratingCache =
			new ConcurrentHashMap<AuthorId, Rating>();

	// The following fields must only be accessed from the UI thread
	private final Set<MessageId> messageIds = new HashSet<MessageId>();
	private String groupName = null;
	private GroupAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from DB threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	private volatile GroupId groupId = null;

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
		loadHeaders();
	}

	private void loadHeaders() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the headers from the database
					Collection<GroupMessageHeader> headers =
							db.getMessageHeaders(groupId);
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + headers.size() + " headers");
					// Display the headers in the UI
					displayHeaders(headers);
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

	private void displayHeaders(final Collection<GroupMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				ratingCache.clear();
				messageIds.clear();
				adapter.clear();
				for(GroupMessageHeader h : headers) {
					Author a = h.getAuthor();
					if(a != null) ratingCache.put(a.getId(), h.getRating());
					messageIds.add(h.getId());
					adapter.add(h);
				}
				adapter.sort(AscendingHeaderComparator.INSTANCE);
				selectFirstUnread();
			}
		});
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			if(!adapter.getItem(i).isRead()) {
				firstUnread = i;
				break;
			}
		}
		if(firstUnread == -1) list.setSelection(count - 1);
		else list.setSelection(firstUnread);
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

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		unbindService(serviceConnection);
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			Message m = g.getMessage();
			if(m.getGroup().getId().equals(groupId))
				loadRatingOrAddToGroup(m, g.isIncoming());
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message removed, reloading");
			loadHeaders(); // FIXME: Don't reload unnecessarily
		} else if(e instanceof RatingChangedEvent) {
			RatingChangedEvent r = (RatingChangedEvent) e;
			AuthorId a = r.getAuthorId();
			ratingCache.remove(a);
			updateRating(a, r.getRating());
		} else if(e instanceof SubscriptionRemovedEvent) {
			if(((SubscriptionRemovedEvent) e).getGroupId().equals(groupId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Subscription removed");
				finishOnUiThread();
			}
		}
	}

	private void loadRatingOrAddToGroup(Message m, boolean incoming) {
		Author a = m.getAuthor();
		if(a == null) {
			addToGroup(m, UNRATED, incoming);
		} else {
			Rating r = ratingCache.get(a.getId());
			if(r == null) loadRating(m, incoming);
			else addToGroup(m, r, incoming);
		}
	}

	private void addToGroup(final Message m, final Rating r,
			final boolean incoming) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(messageIds.add(m.getId())) {
					adapter.add(new GroupMessageHeader(m.getId(), m.getParent(),
							m.getContentType(), m.getSubject(),
							m.getTimestamp(),!incoming, false,
							m.getGroup().getId(), m.getAuthor(), r));
					adapter.sort(AscendingHeaderComparator.INSTANCE);
					selectFirstUnread();
				}
			}
		});
	}

	private void loadRating(final Message m, final boolean incoming) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the rating from the database
					AuthorId a = m.getAuthor().getId();
					Rating r = db.getRating(a);
					// Cache the rating
					ratingCache.put(a, r);
					// Display the message
					addToGroup(m, r, incoming);
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

	private void updateRating(final AuthorId a, final Rating r) {
		runOnUiThread(new Runnable() {
			public void run() {
				boolean affected = false;
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					GroupMessageHeader h = adapter.getItem(i);
					Author author = h.getAuthor();
					if(author != null && author.getId().equals(a)) {
						adapter.remove(h);
						adapter.insert(new GroupMessageHeader(h.getId(),
								h.getParent(), h.getContentType(),
								h.getSubject(), h.getTimestamp(), h.isRead(),
								h.isStarred(), h.getGroupId(), h.getAuthor(),
								r), i);
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
		startActivity(i);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		showMessage(position);
	}

	private void showMessage(int position) {
		GroupMessageHeader item = adapter.getItem(position);
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
}
