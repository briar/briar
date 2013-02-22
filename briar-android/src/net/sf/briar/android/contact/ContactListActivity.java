package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarBinder;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.api.Contact;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout.LayoutParams;

import com.google.inject.Inject;

public class ContactListActivity extends BriarActivity
implements OnClickListener, DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private ArrayAdapter<String> adapter = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_expandable_list_item_1,
				new ArrayList<String>());
		ListView listView = new ListView(this);
		listView.setAdapter(adapter);
		layout.addView(listView);

		Button addContactButton = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		addContactButton.setLayoutParams(lp);
		addContactButton.setText(R.string.add_contact_button);
		addContactButton.setCompoundDrawablesWithIntrinsicBounds(
				R.drawable.social_add_person, 0, 0, 0);
		addContactButton.setOnClickListener(this);
		layout.addView(addContactButton);

		setContentView(layout);
	
		// Listen for database events
		db.addListener(this);

		// Bind to the service
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);

		// Load the contact list from the DB
		reloadContactList();

		// Add some fake contacts to the database in a background thread
		// FIXME: Remove this
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					((BriarBinder) binder).getService().waitForStartup();
					if(LOG.isLoggable(INFO)) LOG.info("Service started");
					// Insert a couple of fake contacts
					db.addContact("Alice");
					db.addContact("Bob");
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

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactAddedEvent) reloadContactList();
	}

	private void reloadContactList() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					((BriarBinder) binder).getService().waitForStartup();
					// Load the contacts from the database
					final Collection<Contact> contacts = db.getContacts();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + contacts.size() + " contacts");
					// Update the contact list on the UI thread
					runOnUiThread(new Runnable() {
						public void run() {
							updateContactList(contacts);
						}
					});
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

	private void updateContactList(Collection<Contact> contacts) {
		List<String> names = new ArrayList<String>(contacts.size());
		for(Contact c : contacts) names.add(c.getName());
		Collections.sort(names);
		adapter.clear();
		for(String name : names) adapter.add(name);
	}
}
