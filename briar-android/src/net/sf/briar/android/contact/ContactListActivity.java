package net.sf.briar.android.contact;

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
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.util.HorizontalBorder;
import net.sf.briar.android.util.ListLoadingProgressBar;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.transport.ConnectionListener;
import net.sf.briar.api.transport.ConnectionRegistry;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

public class ContactListActivity extends RoboActivity
implements OnClickListener, OnItemClickListener, DatabaseListener,
ConnectionListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	@Inject private ConnectionRegistry connectionRegistry;
	private ContactListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton addContactButton = null;

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

		adapter = new ContactListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		layout.addView(list);

		// Show a progress bar while the list is loading
		list.setVisibility(GONE);
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		addContactButton = new ImageButton(this);
		addContactButton.setBackgroundResource(0);
		addContactButton.setImageResource(R.drawable.social_add_person);
		addContactButton.setOnClickListener(this);
		layout.addView(addContactButton);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		connectionRegistry.addListener(this);
		loadContacts();
	}

	private void loadContacts() {
		clearContacts();
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Map<ContactId, Long> times = db.getLastConnected();
					for(Contact c : db.getContacts()) {
						Long lastConnected = times.get(c.getId());
						if(lastConnected == null) continue;
						try {
							GroupId inbox = db.getInboxGroupId(c.getId());
							Collection<MessageHeader> headers =
									db.getInboxMessageHeaders(c.getId());
							displayContact(c, lastConnected, inbox, headers);
						} catch(NoSuchContactException e) {
							if(LOG.isLoggable(INFO))
								LOG.info("Contact removed");
						}
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					hideProgressBar();
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

	private void clearContacts() {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void displayContact(final Contact c, final long lastConnected,
			final GroupId inbox, final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				boolean connected = connectionRegistry.isConnected(c.getId());
				// Remove the old item, if any
				ContactListItem item = findItem(c.getId());
				if(item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new ContactListItem(c, connected, lastConnected,
						inbox, headers));
				adapter.sort(ItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void hideProgressBar() {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
			}
		});
	}

	private ContactListItem findItem(ContactId c) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			ContactListItem item = adapter.getItem(i);
			if(item.getContact().getId().equals(c)) return item;
		}
		return null; // Not found
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
		connectionRegistry.removeListener(this);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ContactListItem item = adapter.getItem(position);
		ContactId contactId = item.getContact().getId();
		String contactName = item.getContact().getAuthor().getName();
		GroupId inbox = item.getInboxGroupId();
		AuthorId localAuthorId = item.getContact().getLocalAuthorId();
		Intent i = new Intent(this, ConversationActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
		i.putExtra("net.sf.briar.GROUP_ID", inbox.getBytes());
		i.putExtra("net.sf.briar.LOCAL_AUTHOR_ID", localAuthorId.getBytes());
		startActivity(i);
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactAddedEvent) {
			loadContacts();
		} else if(e instanceof ContactRemovedEvent) {
			// Reload the conversation, expecting NoSuchContactException
			if(LOG.isLoggable(INFO)) LOG.info("Contact removed, reloading");
			reloadContact(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof MessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			ContactId source = ((MessageAddedEvent) e).getContactId();
			if(source == null) loadContacts();
			else reloadContact(source);
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadContacts();
		}
	}

	private void reloadContact(final ContactId c) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<MessageHeader> headers =
							db.getInboxMessageHeaders(c);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					updateItem(c, headers);
				} catch(NoSuchContactException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
					removeItem(c);
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

	private void updateItem(final ContactId c,
			final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item != null) item.setHeaders(headers);
			}
		});
	}

	private void removeItem(final ContactId c) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item != null) {
					adapter.remove(item);
					adapter.notifyDataSetChanged();
				}
			}
		});
	}

	public void contactConnected(ContactId c) {
		setConnected(c, true);
	}

	public void contactDisconnected(ContactId c) {
		setConnected(c, false);
	}

	private void setConnected(final ContactId c, final boolean connected) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item == null) return;
				if(LOG.isLoggable(INFO)) LOG.info("Updating connection time");
				item.setConnected(connected);
				item.setLastConnected(System.currentTimeMillis());
				list.invalidateViews();
			}
		});
	}

	private static class ItemComparator implements Comparator<ContactListItem> {

		private static final ItemComparator INSTANCE = new ItemComparator();

		public int compare(ContactListItem a, ContactListItem b) {
			String aName = a.getContact().getAuthor().getName();
			String bName = b.getContact().getAuthor().getName();
			return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
		}
	}
}
