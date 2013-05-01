package net.sf.briar.android.contact;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_STREAM;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP_1;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.android.widgets.ListLoadingProgressBar;
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
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class ContactListActivity extends RoboActivity
implements OnClickListener, OnItemClickListener, DatabaseListener,
ConnectionListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private ConnectionRegistry connectionRegistry;
	private ContactListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton addContactButton = null, shareButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

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

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		footer.addView(new HorizontalSpace(this));

		addContactButton = new ImageButton(this);
		addContactButton.setBackgroundResource(0);
		addContactButton.setImageResource(R.drawable.social_add_person);
		addContactButton.setOnClickListener(this);
		footer.addView(addContactButton);
		footer.addView(new HorizontalSpace(this));

		shareButton = new ImageButton(this);
		shareButton.setBackgroundResource(0);
		shareButton.setImageResource(R.drawable.social_share);
		shareButton.setOnClickListener(this);
		footer.addView(shareButton);
		footer.addView(new HorizontalSpace(this));
		layout.addView(footer);

		setContentView(layout);

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		connectionRegistry.addListener(this);
		loadContacts();
	}

	private void loadContacts() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<Contact> contacts = db.getContacts();
					Map<ContactId, Long> times = db.getLastConnected();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts(contacts, times);
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

	private void displayContacts(final Collection<Contact> contacts,
			final Map<ContactId, Long> times) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				adapter.clear();
				for(Contact c : contacts) {
					boolean now = connectionRegistry.isConnected(c.getId());
					Long last = times.get(c.getId());
					if(last != null)
						adapter.add(new ContactListItem(c, now, last));
				}
				adapter.sort(ItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
		connectionRegistry.removeListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == addContactButton) {
			startActivity(new Intent(this, AddContactActivity.class));
		} else if(view == shareButton) {
			String apkPath = getPackageCodePath();
			Intent i = new Intent(ACTION_SEND);
			i.setType("application/*");
			i.putExtra(EXTRA_STREAM, Uri.fromFile(new File(apkPath)));
			String shareApp = getResources().getString(R.string.share_app);
			startActivity(Intent.createChooser(i, shareApp));
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// FIXME: Hook this up to an activity
	}

	public void eventOccurred(DatabaseEvent e) {
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
						if(LOG.isLoggable(INFO))
							LOG.info("Updating connection time");
						item.setConnected(connected);
						item.setLastConnected(System.currentTimeMillis());
						list.invalidateViews();
						return;
					}
				}
			}
		});
	}

	private static class ItemComparator implements Comparator<ContactListItem> {

		private static final ItemComparator INSTANCE = new ItemComparator();

		public int compare(ContactListItem a, ContactListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.getContactName(),
					b.getContactName());
		}
	}
}
