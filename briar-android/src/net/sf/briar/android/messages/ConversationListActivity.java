package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.util.HorizontalBorder;
import net.sf.briar.android.util.ListLoadingProgressBar;
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
import net.sf.briar.api.lifecycle.LifecycleManager;
import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class ConversationListActivity extends RoboFragmentActivity
implements OnClickListener, DatabaseListener, NoContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ConversationListActivity.class.getName());

	private ConversationListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private NoContactsDialog noContactsDialog = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
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

		// Show a progress bar while the list is loading
		list.setVisibility(GONE);
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		ImageButton composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		layout.addView(composeButton);

		setContentView(layout);

		FragmentManager fm = getSupportFragmentManager();
		Fragment f = fm.findFragmentByTag("NoContactsDialog");
		if(f == null) noContactsDialog = new NoContactsDialog();
		else noContactsDialog = (NoContactsDialog) f;
		noContactsDialog.setListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		clearHeaders();
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
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
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void clearHeaders() {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void displayHeaders(final Contact c,
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				// Remove the old item, if any
				ConversationListItem item = findConversation(c.getId());
				if(item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new ConversationListItem(c, headers));
				adapter.sort(ItemComparator.INSTANCE);
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

	public void onClick(View view) {
		if(adapter.isEmpty()) {
			FragmentManager fm = getSupportFragmentManager();
			noContactsDialog.show(fm, "NoContactsDialog");
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
					lifecycleManager.waitForDatabase();
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
						LOG.info("Interrupted while waiting for database");
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

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {}

	private static class ItemComparator
	implements Comparator<ConversationListItem> {

		private static final ItemComparator INSTANCE = new ItemComparator();

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
