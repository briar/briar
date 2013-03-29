package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.transport.ConnectionListener;
import net.sf.briar.api.transport.ConnectionRegistry;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class ContactListActivity extends BriarActivity
implements OnClickListener, DatabaseListener, ConnectionListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private ConnectionRegistry connectionRegistry;
	private ContactListAdapter adapter = null;

	// Fields that are accessed from DB threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ContactListAdapter(this);
		ListView list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(CommonLayoutParams.MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(adapter);
		layout.addView(list);

		layout.addView(new HorizontalBorder(this));

		ImageButton addContactButton = new ImageButton(this);
		addContactButton.setBackgroundResource(0);
		addContactButton.setImageResource(R.drawable.social_add_person);
		addContactButton.setOnClickListener(this);
		layout.addView(addContactButton);

		setContentView(layout);

		// Listen for contacts being added or removed
		db.addListener(this);
		// Listen for contacts connecting or disconnecting
		connectionRegistry.addListener(this);
		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadContacts();
	}

	private void loadContacts() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the contacts from the database
					Collection<Contact> contacts = db.getContacts();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + contacts.size() + " contacts");
					// Display the contacts in the UI
					displayContacts(contacts);
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

	private void displayContacts(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(Contact c : contacts) {
					boolean conn = connectionRegistry.isConnected(c.getId());
					adapter.add(new ContactListItem(c, conn));
				}
				adapter.sort(ContactComparator.INSTANCE);
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		connectionRegistry.removeListener(this);
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void eventOccurred(DatabaseEvent e) {
		// These events should be rare, so just reload the list
		if(e instanceof ContactAddedEvent) loadContacts();
		else if(e instanceof ContactRemovedEvent) loadContacts();
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
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					ContactListItem item = adapter.getItem(i);
					if(item.getContactId().equals(c)) {
						item.setConnected(connected);
						return;
					}
				}
			}
		});
	}

	private static class ContactComparator
	implements Comparator<ContactListItem> {

		static final ContactComparator INSTANCE = new ContactComparator();

		public int compare(ContactListItem a, ContactListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.getContactName(),
					b.getContactName());
		}
	}
}
