package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
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

public class ConversationListActivity extends BriarActivity
implements OnClickListener, DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private ConversationListAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from DB threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	@Inject private volatile MessageFactory messageFactory;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ConversationListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(CommonLayoutParams.MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(adapter);
		layout.addView(list);

		layout.addView(new HorizontalBorder(this));

		ImageButton composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		layout.addView(composeButton);

		setContentView(layout);

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
					// If there are no messages in the DB, create some fake ones
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders();
					if(!headers.isEmpty()) return;
					if(LOG.isLoggable(INFO))
						LOG.info("Inserting fake private messages");
					// We'll also need a contact to exchange messages with
					ContactId contactId = db.addContact("Carol");
					// Insert some text messages to and from the contact
					for(int i = 0; i < 20; i++) {
						String body;
						if(i % 3 == 0) {
							body = "Message " + i + " is short.";
						} else { 
							body = "Message " + i + " is long enough to"
									+ " wrap onto a second line on some"
									+ " screens.";
						}
						Message m = messageFactory.createPrivateMessage(null,
								"text/plain", body.getBytes("UTF-8"));
						if(Math.random() < 0.5)
							db.addLocalPrivateMessage(m, contactId);
						else db.receiveMessage(contactId, m);
						db.setReadFlag(m.getId(), i % 4 == 0);
					}
					// Insert a non-text message
					Message m = messageFactory.createPrivateMessage(null,
							"image/jpeg", new byte[1000]);
					db.receiveMessage(contactId, m);
					// Insert a long text message
					StringBuilder s = new StringBuilder();
					for(int i = 0; i < 100; i++)
						s.append("This is a very tedious message. ");
					m = messageFactory.createPrivateMessage(m.getId(),
							"text/plain", s.toString().getBytes("UTF-8"));
					db.addLocalPrivateMessage(m, contactId);
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
		db.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the contact list from the database
					for(Contact c : db.getContacts()) {
						try {
							// Load the headers from the database
							Collection<PrivateMessageHeader> headers =
									db.getPrivateMessageHeaders(c.getId());
							// Display the headers in the UI
							displayHeaders(c, headers);
						} catch(NoSuchContactException e) {
							if(LOG.isLoggable(INFO))
								LOG.info("Contact removed");
						}
					}
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

	private void displayHeaders(final Contact c,
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				// Remove the old item, if any
				ConversationListItem item = findConversation(c.getId());
				if(item != null) adapter.remove(item);
				// Add a new item if there are any headers to display
				if(!headers.isEmpty()) {
					List<PrivateMessageHeader> headerList =
							new ArrayList<PrivateMessageHeader>(headers);
					adapter.add(new ConversationListItem(c, headerList));
					adapter.sort(ConversationComparator.INSTANCE);
				}
				selectFirstUnread();
			}
		});
	}

	private ConversationListItem findConversation(ContactId c) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			ConversationListItem item = adapter.getItem(i);
			if(item.getContactId().equals(c)) return item;
		}
		return null; // Not found
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
	public void onPause() {
		super.onPause();
		db.removeListener(this);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, WritePrivateMessageActivity.class));
	}

	// FIXME: Load operations may overlap, resulting in an inconsistent view
	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Removing conversation");
			removeConversation(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders(); // FIXME: Don't reload everything
		} else if(e instanceof PrivateMessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			loadHeaders(((PrivateMessageAddedEvent) e).getContactId());
		}
	}

	private void removeConversation(final ContactId c) {
		runOnUiThread(new Runnable() {
			public void run() {
				ConversationListItem item = findConversation(c);
				if(item != null) {
					adapter.remove(item);
					selectFirstUnread();
				}
			}
		});
	}

	private void loadHeaders(final ContactId c) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					Contact contact = db.getContact(c);
					displayHeaders(contact, db.getPrivateMessageHeaders(c));
				} catch(NoSuchContactException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
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

	private static class ConversationComparator
	implements Comparator<ConversationListItem> {

		static final ConversationComparator INSTANCE =
				new ConversationComparator();

		public int compare(ConversationListItem a, ConversationListItem b) {
			// The item with the newest message comes first
			long aTime = a.getTimestamp(), bTime = b.getTimestamp();
			if(aTime > bTime) return -1;
			if(aTime < bTime) return 1;
			return 0;
		}
	}
}
