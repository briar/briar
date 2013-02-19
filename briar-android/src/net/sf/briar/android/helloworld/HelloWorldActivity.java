package net.sf.briar.android.helloworld;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
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
import android.widget.TextView;

import com.google.inject.Inject;

public class HelloWorldActivity extends BriarActivity
implements OnClickListener, DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(HelloWorldActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	Button addContact = null, quit = null;
	private ArrayAdapter<String> adapter = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView welcome = new TextView(this);
		welcome.setPadding(0, 0, 0, 10);
		welcome.setText(R.string.welcome);
		layout.addView(welcome);

		TextView faceToFace = new TextView(this);
		faceToFace.setPadding(0, 0, 0, 10);
		faceToFace.setText(R.string.face_to_face);
		layout.addView(faceToFace);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		addContact = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		addContact.setLayoutParams(lp);
		addContact.setText(R.string.add_contact_button);
		addContact.setCompoundDrawablesWithIntrinsicBounds(
				R.drawable.social_add_person, 0, 0, 0);
		addContact.setOnClickListener(this);
		innerLayout.addView(addContact);

		quit = new Button(this);
		quit.setLayoutParams(lp);
		quit.setText(R.string.quit_button);
		quit.setOnClickListener(this);
		innerLayout.addView(quit);
		layout.addView(innerLayout);

		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_expandable_list_item_1,
				new ArrayList<String>());
		ListView listView = new ListView(this);
		listView.setAdapter(adapter);
		layout.addView(listView);

		setContentView(layout);

		// Listen for database events
		db.addListener(this);

		// Start the service and bind to it
		startService(new Intent(BriarService.class.getName()));
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);

		// Add some fake contacts to the database in a background thread
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

	public void onClick(View view) {
		if(view == addContact)
			startActivity(new Intent(this, AddContactActivity.class));
		else if(view == quit)
			quit();
	}

	private void quit() {
		new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					BriarService service = ((BriarBinder) binder).getService();
					service.waitForStartup();
					// Shut down the service and wait for it to shut down
					if(LOG.isLoggable(INFO)) LOG.info("Shutting down service");
					service.shutdown();
					service.waitForShutdown();
					// Unbind from the service, finish the activity, and die
					runOnUiThread(new Runnable() {
						public void run() {
							unbindService(serviceConnection);
							finish();
							if(LOG.isLoggable(INFO)) LOG.info("Exiting");
							System.exit(0);
						}
					});
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
				}
			}
		}.start();
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
