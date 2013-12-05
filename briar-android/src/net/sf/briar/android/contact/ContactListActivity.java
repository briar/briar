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
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.groups.NoContactsDialog;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.util.HorizontalBorder;
import net.sf.briar.android.util.HorizontalSpace;
import net.sf.briar.android.util.ListLoadingProgressBar;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.transport.ConnectionListener;
import net.sf.briar.api.transport.ConnectionRegistry;
import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

public class ContactListActivity extends RoboFragmentActivity
implements OnClickListener, DatabaseListener, ConnectionListener,
NoContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	@Inject private ConnectionRegistry connectionRegistry;
	private ContactListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton addContactButton = null, composeButton = null;
	private ImageButton shareButton = null;
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

		adapter = new ContactListAdapter(this);
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

		composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		footer.addView(composeButton);
		footer.addView(new HorizontalSpace(this));

		shareButton = new ImageButton(this);
		shareButton.setBackgroundResource(0);
		shareButton.setImageResource(R.drawable.social_share);
		shareButton.setOnClickListener(this);
		footer.addView(shareButton);
		footer.addView(new HorizontalSpace(this));
		layout.addView(footer);

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
		connectionRegistry.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		clearHeaders();
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
							Collection<PrivateMessageHeader> headers =
									db.getPrivateMessageHeaders(c.getId());
							displayHeaders(c, lastConnected, headers);
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

	private void displayHeaders(final Contact c, final long lastConnected,
			final Collection<PrivateMessageHeader> headers) {
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
						headers));
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
			if(item.getContactId().equals(c)) return item;
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
		if(view == addContactButton) {
			startActivity(new Intent(this, AddContactActivity.class));
		} else if(view == composeButton) {
			if(adapter.isEmpty()) {
				FragmentManager fm = getSupportFragmentManager();
				noContactsDialog.show(fm, "NoContactsDialog");
			} else {
				startActivity(new Intent(this, WritePrivateMessageActivity.class));
			}
		} else if(view == shareButton) {
			String apkPath = getPackageCodePath();
			Intent i = new Intent(ACTION_SEND);
			i.setType("application/*");
			i.putExtra(EXTRA_STREAM, Uri.fromFile(new File(apkPath)));
			String shareApp = getResources().getString(R.string.share_app);
			startActivity(Intent.createChooser(i, shareApp));
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactAddedEvent) {
			loadHeaders();
		} else if(e instanceof ContactRemovedEvent) {
			// Reload the conversation, expecting NoSuchContactException
			if(LOG.isLoggable(INFO)) LOG.info("Contact removed, reloading");
			reloadHeaders(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders();
		} else if(e instanceof PrivateMessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			reloadHeaders(((PrivateMessageAddedEvent) e).getContactId());
		}
	}

	private void reloadHeaders(final ContactId c) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders(c);
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
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item == null) return;
				// Replace the item with a new item containing the new headers
				adapter.remove(item);
				item = new ContactListItem(item.getContact(),
						item.isConnected(), item.getLastConnected(), headers);
				adapter.add(item);
				adapter.sort(ItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void removeItem(final ContactId c) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item != null) adapter.remove(item);
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

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {}

	private static class ItemComparator implements Comparator<ContactListItem> {

		private static final ItemComparator INSTANCE = new ItemComparator();

		public int compare(ContactListItem a, ContactListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.getContactName(),
					b.getContactName());
		}
	}
}
