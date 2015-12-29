package org.briarproject.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ContactListActivity extends BriarActivity
		implements OnCreateContextMenuListener, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	@Inject private ConnectionRegistry connectionRegistry;
	private TextView empty = null;
	private ContactListAdapter adapter = null;
	private RecyclerView list = null;
	private ProgressBar loading = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ContactManager contactManager;
	@Inject private volatile MessagingManager messagingManager;
	@Inject private volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_contact_list);

		adapter = new ContactListAdapter(this);
		list = (RecyclerView) findViewById(R.id.contactList);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setOnCreateContextMenuListener(this);
		list.setVisibility(GONE);

		// Show a notice when there are no contacts
		empty = (TextView) findViewById(R.id.emptyView);

		// Show a progress bar while the list is loading
		loading = (ProgressBar) findViewById(R.id.progressBar);
		loading.setVisibility(VISIBLE);

		// Show a floating action button
		FloatingActionButton fab = (FloatingActionButton) findViewById(
				R.id.addContactFAB);

		// handle FAB click
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(ContactListActivity.this,
						AddContactActivity.class));
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadContacts();
	}


	private void loadContacts() {
		runOnUiThread(new Runnable() {
			public void run() {
				empty.setVisibility(GONE);
				list.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
			}
		});
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts =
							new ArrayList<ContactListItem>();
					for (Contact c : contactManager.getContacts()) {
						try {
							ContactId id = c.getId();
							GroupId conversation =
									messagingManager.getConversationId(id);
							Collection<PrivateMessageHeader> headers =
									messagingManager.getMessageHeaders(id);

							boolean connected =
									connectionRegistry.isConnected(c.getId());
							contacts.add(new ContactListItem(c, connected,
									conversation,
									headers));
						} catch (NoSuchContactException e) {
							// Continue
						}
					}
					displayContacts(contacts);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final List<ContactListItem> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (contacts.size() > 0) {
					list.setVisibility(VISIBLE);
					empty.setVisibility(GONE);
				} else {
					list.setVisibility(GONE);
					empty.setVisibility(VISIBLE);
				}
				loading.setVisibility(GONE);

				adapter.addAll(contacts);
			}
		});
	}

	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			LOG.info("Contact added, reloading");
			loadContacts();
		} else if (e instanceof ContactConnectedEvent) {
			setConnected(((ContactConnectedEvent) e).getContactId(), true);
		} else if (e instanceof ContactDisconnectedEvent) {
			setConnected(((ContactDisconnectedEvent) e).getContactId(), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed");
			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof MessageAddedEvent) {
			LOG.info("Message added, reloading");
			ContactId source = ((MessageAddedEvent) e).getContactId();
			if (source == null) loadContacts();
			else reloadContact(source);
		}
	}

	private void reloadContact(final ContactId c) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<PrivateMessageHeader> headers =
							messagingManager.getMessageHeaders(c);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					updateItem(c, headers);
				} catch (NoSuchContactException e) {
					removeItem(c);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void updateItem(final ContactId c,
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItem(position);
				if (item != null) {
					item.setHeaders(headers);
					adapter.updateItem(position, item);
				}
			}
		});
	}

	private void removeItem(final ContactId c) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = adapter.findItem(c);
				if (item != null) {
					adapter.remove(item);

					if (adapter.isEmpty()) {
						empty.setVisibility(VISIBLE);
						list.setVisibility(GONE);
					}
				}
			}
		});
	}

	private void setConnected(final ContactId c, final boolean connected) {
		runOnUiThread(new Runnable() {
			public void run() {
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItem(position);
				if (item != null) {
					item.setConnected(connected);
					adapter.notifyItemChanged(position);
				}
			}
		});
	}
}
