package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.Rating.BAD;
import static net.sf.briar.api.Rating.GOOD;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.AuthorFactory;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class GroupListActivity extends BriarActivity
implements OnClickListener, DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(GroupListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private GroupListAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from DB threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	@Inject private volatile AuthorFactory authorFactory;
	@Inject private volatile GroupFactory groupFactory;
	@Inject private volatile MessageFactory messageFactory;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new GroupListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(CommonLayoutParams.MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(adapter);
		layout.addView(list);

		layout.addView(new HorizontalBorder(this));

		ImageButton newGroupButton = new ImageButton(this);
		newGroupButton.setBackgroundResource(0);
		newGroupButton.setImageResource(R.drawable.social_new_chat);
		newGroupButton.setOnClickListener(this);
		layout.addView(newGroupButton);

		setContentView(layout);

		// Listen for messages and groups being added or removed
		db.addListener(this);
		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);

		// Add some fake messages to the database in a background thread
		insertFakeMessages();
	}

	// FIXME: Remove this
	private void insertFakeMessages() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// If there are no groups in the DB, create some fake ones
					Collection<Group> groups = db.getSubscriptions();
					if(!groups.isEmpty()) return;
					if(LOG.isLoggable(INFO))
						LOG.info("Inserting fake groups and messages");
					// We'll also need a contact to receive messages from
					ContactId contactId = db.addContact("Dave");
					// Finally, we'll need some authors for the messages
					KeyPair keyPair = crypto.generateSignatureKeyPair();
					byte[] publicKey = keyPair.getPublic().getEncoded();
					PrivateKey privateKey = keyPair.getPrivate();
					Author author = authorFactory.createAuthor("Batman",
							publicKey);
					db.setRating(author.getId(), BAD);
					Author author1 = authorFactory.createAuthor("Duckman",
							publicKey);
					db.setRating(author1.getId(), GOOD);
					// Insert some fake groups and make them visible
					Group group = groupFactory.createGroup("DisneyLeaks");
					db.subscribe(group);
					db.setVisibility(group.getId(), Arrays.asList(contactId));
					Group group1 = groupFactory.createGroup("Godwin's Lore");
					db.subscribe(group1);
					db.setVisibility(group1.getId(), Arrays.asList(contactId));
					// Insert some text messages to the groups
					for(int i = 0; i < 20; i++) {
						String body;
						if(i % 3 == 0) {
							body = "Message " + i + " is short.";
						} else { 
							body = "Message " + i + " is long enough to wrap"
									+ " onto a second line on some screens.";
						}
						Group g = i % 2 == 0 ? group : group1;
						Message m;
						if(i % 5 == 0) {
							m = messageFactory.createAnonymousMessage(null, g,
									"text/plain", body.getBytes("UTF-8"));
						} else if(i % 5 == 2) {
							m = messageFactory.createPseudonymousMessage(null,
									g, author, privateKey, "text/plain",
									body.getBytes("UTF-8"));
						} else {
							m = messageFactory.createPseudonymousMessage(null,
									g, author1, privateKey, "text/plain",
									body.getBytes("UTF-8"));
						}
						if(Math.random() < 0.5) db.addLocalGroupMessage(m);
						else db.receiveMessage(contactId, m);
						db.setReadFlag(m.getId(), i % 4 == 0);
					}
					// Insert a non-text message
					Message m = messageFactory.createAnonymousMessage(null,
							group, "image/jpeg", new byte[1000]);
					db.receiveMessage(contactId, m);
					// Insert a long text message
					StringBuilder s = new StringBuilder();
					for(int i = 0; i < 100; i++)
						s.append("This is a very tedious message. ");
					String body = s.toString();
					m = messageFactory.createAnonymousMessage(m.getId(),
							group1, "text/plain", body.getBytes("UTF-8"));
					db.addLocalGroupMessage(m);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(GeneralSecurityException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		loadGroups();
	}

	private void loadGroups() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the subscribed groups from the DB
					Collection<Group> groups = db.getSubscriptions();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + groups.size() + " groups");
					List<GroupListItem> items = new ArrayList<GroupListItem>();
					for(Group g : groups) {
						// Filter out restricted groups
						if(g.getPublicKey() != null) continue;
						// Load the message headers
						Collection<GroupMessageHeader> headers;
						try {
							headers = db.getMessageHeaders(g.getId());
						} catch(NoSuchSubscriptionException e) {
							continue; // Unsubscribed since getSubscriptions()
						}
						if(LOG.isLoggable(INFO))
							LOG.info("Loaded " + headers.size() + " headers");
						if(!headers.isEmpty())
							items.add(createItem(g, headers));
					}
					// Display the groups in the UI
					displayGroups(Collections.unmodifiableList(items));
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

	private GroupListItem createItem(Group group,
			Collection<GroupMessageHeader> headers) {
		List<GroupMessageHeader> sort =
				new ArrayList<GroupMessageHeader>(headers);
		Collections.sort(sort, DescendingHeaderComparator.INSTANCE);
		return new GroupListItem(group, sort);
	}

	private void displayGroups(final Collection<GroupListItem> items) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(GroupListItem i : items) adapter.add(i);
				adapter.sort(GroupComparator.INSTANCE);
				selectFirstUnread();
			}
		});
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			if(adapter.getItem(i).getUnreadCount() > 0) {
				firstUnread = i;
				break;
			}
		}
		if(firstUnread == -1) list.setSelection(count - 1);
		else list.setSelection(firstUnread);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, WriteGroupMessageActivity.class));
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			addToGroup(g.getMessage(), g.isIncoming());
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message removed, reloading");
			loadGroups(); // FIXME: Don't reload unnecessarily
		} else if(e instanceof SubscriptionRemovedEvent) {
			removeGroup(((SubscriptionRemovedEvent) e).getGroupId());
		}
	}

	private void addToGroup(final Message m, final boolean incoming) {
		runOnUiThread(new Runnable() {
			public void run() {
				GroupId g = m.getGroup().getId();
				GroupListItem item = findGroup(g);
				if(item == null) {
					loadGroup(g, m, incoming);
				} else if(item.add(m, incoming)) {
					adapter.sort(GroupComparator.INSTANCE);
					selectFirstUnread();
					list.invalidate();
				}
			}
		});
	}

	private GroupListItem findGroup(GroupId g) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			GroupListItem item = adapter.getItem(i);
			if(item.getGroupId().equals(g)) return item;
		}
		return null; // Not found
	}

	private void loadGroup(final GroupId g, final Message m,
			final boolean incoming) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the group from the DB and display it in the UI
					displayGroup(db.getGroup(g), m, incoming);
				} catch(NoSuchSubscriptionException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Subscription removed");
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

	private void displayGroup(final Group g, final Message m,
			final boolean incoming) {
		runOnUiThread(new Runnable() {
			public void run() {
				// The item may have been added since loadGroup() was called
				GroupListItem item = findGroup(g.getId());
				if(item == null) {
					adapter.add(new GroupListItem(g, m, incoming));
					adapter.sort(GroupComparator.INSTANCE);
					selectFirstUnread();
				} else if(item.add(m, incoming)) {
					adapter.sort(GroupComparator.INSTANCE);
					selectFirstUnread();
					list.invalidate();
				}
			}
		});
	}

	private void removeGroup(final GroupId g) {
		runOnUiThread(new Runnable() {
			public void run() {
				GroupListItem item = findGroup(g);
				if(item != null) {
					adapter.remove(item);
					selectFirstUnread();
				}
			}
		});
	}

	private static class GroupComparator implements Comparator<GroupListItem> {

		private static final GroupComparator INSTANCE = new GroupComparator();

		public int compare(GroupListItem a, GroupListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.getGroupName(),
					b.getGroupName());
		}
	}
}