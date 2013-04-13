package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP_1;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarFragmentActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class ConversationListActivity extends BriarFragmentActivity
implements OnClickListener, DatabaseListener, NoContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ConversationListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private ConversationListAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ConversationListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(MATCH_WRAP_1);
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

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					for(Contact c : db.getContacts()) {
						try {
							Collection<PrivateMessageHeader> headers =
									db.getPrivateMessageHeaders(c.getId());
							displayHeaders(c, headers);
						} catch(NoSuchContactException e) {
							if(LOG.isLoggable(INFO))
								LOG.info("Contact removed");
						}
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
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
				// Add a new item
				adapter.add(new ConversationListItem(c, headers));
				adapter.sort(ConversationComparator.INSTANCE);
				adapter.notifyDataSetChanged();
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
		if(adapter.isEmpty()) {
			NoContactsDialog dialog = new NoContactsDialog();
			dialog.setListener(this);
			dialog.show(getSupportFragmentManager(), "NoContactsDialog");
		} else {
			startActivity(new Intent(this, WritePrivateMessageActivity.class));
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			// Reload the conversation, expecting NoSuchContactException
			if(LOG.isLoggable(INFO)) LOG.info("Contact removed, reloading");
			loadHeaders(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders();
		} else if(e instanceof PrivateMessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			loadHeaders(((PrivateMessageAddedEvent) e).getContactId());
		}
	}

	private void loadHeaders(final ContactId c) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					Contact contact = db.getContact(c);
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders(c);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					displayHeaders(contact, headers);
				} catch(NoSuchContactException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
					removeConversation(c);
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

	public void addContactButtonClicked() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void cancelButtonClicked() {
		// That's nice dear
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
			// Break ties by contact name
			String aName = a.getContactName(), bName = b.getContactName();
			return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
		}
	}
}
