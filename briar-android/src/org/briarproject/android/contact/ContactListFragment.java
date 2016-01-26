package org.briarproject.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseEventFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ContactListFragment extends BaseEventFragment {

	private static final Logger LOG =
			Logger.getLogger(ContactListFragment.class.getName());

	public final static String TAG = "ContactListFragment";

	public static ContactListFragment newInstance() {

		Bundle args = new Bundle();

		ContactListFragment fragment = new ContactListFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Inject
	private CryptoComponent crypto;
	@Inject
	private ConnectionRegistry connectionRegistry;
	private ContactListAdapter adapter = null;
	private BriarRecyclerView list = null;

	// Fields that are accessed from background threads must be volatile
	@Inject
	private volatile ContactManager contactManager;
	@Inject
	private volatile MessagingManager messagingManager;
	@Inject
	private volatile EventBus eventBus;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View contentView =
				inflater.inflate(R.layout.activity_contact_list, container,
						false);

		adapter = new ContactListAdapter(getContext(), crypto);
		list = (BriarRecyclerView) contentView.findViewById(R.id.contactList);
		list.setLayoutManager(new LinearLayoutManager(getContext()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));

		// Show a floating action button
		FloatingActionButton fab =
				(FloatingActionButton) contentView.findViewById(
						R.id.addContactFAB);

		// handle FAB click
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(getContext(),
						AddContactActivity.class));
			}
		});

		return contentView;
	}


	@Override
	public void onResume() {
		super.onResume();

		loadContacts();
	}


	private void loadContacts() {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts =
							new ArrayList<ContactListItem>();
					for (Contact c : contactManager.getContacts()) {
						try {
							ContactId id = c.getId();
							GroupId groupId =
									messagingManager.getConversationId(id);
							Collection<PrivateMessageHeader> headers =
									messagingManager.getMessageHeaders(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							contacts.add(new ContactListItem(c, connected,
									groupId, headers));
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
		listener.runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				if (contacts.size() == 0) list.showData();
				else adapter.addAll(contacts);
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
		} else if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			ClientId c = m.getClientId();
			if (m.isValid() && c.equals(messagingManager.getClientId())) {
				LOG.info("Message added, reloading");
				reloadConversation(m.getMessage().getGroupId());
			}
		}
	}

	private void reloadConversation(final GroupId g) {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					ContactId c = messagingManager.getContactId(g);
					Collection<PrivateMessageHeader> headers =
							messagingManager.getMessageHeaders(c);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					updateItem(c, headers);
				} catch (NoSuchContactException e) {
					LOG.info("Contact removed");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void updateItem(final ContactId c,
			final Collection<PrivateMessageHeader> headers) {
		listener.runOnUiThread(new Runnable() {
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
		listener.runOnUiThread(new Runnable() {
			public void run() {
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItem(position);
				if (item != null) adapter.remove(item);
			}
		});
	}

	private void setConnected(final ContactId c, final boolean connected) {
		listener.runOnUiThread(new Runnable() {
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
