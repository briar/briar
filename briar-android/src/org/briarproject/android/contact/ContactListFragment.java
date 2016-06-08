package org.briarproject.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionMessage;
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

import static android.support.v4.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;

public class ContactListFragment extends BaseFragment implements EventListener {

	public final static String TAG = "ContactListFragment";

	private static final Logger LOG =
			Logger.getLogger(ContactListFragment.class.getName());

	@Inject
	protected ConnectionRegistry connectionRegistry;
	@Inject
	protected EventBus eventBus;

	private ContactListAdapter adapter = null;
	private BriarRecyclerView list = null;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected volatile MessagingManager messagingManager;
	@Inject
	protected volatile IntroductionManager introductionManager;
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	@Inject
	public ContactListFragment() {

	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View contentView =
				inflater.inflate(R.layout.fragment_contact_list, container,
						false);

		BaseContactListAdapter.OnItemClickListener onItemClickListener =
				new ContactListAdapter.OnItemClickListener() {
					@Override
					public void onItemClick(View view, ContactListItem item) {

						GroupId groupId = item.getGroupId();
						Intent i = new Intent(getActivity(),
								ConversationActivity.class);
						i.putExtra(GROUP_ID, groupId.getBytes());

						ContactListAdapter.ContactHolder holder =
								(ContactListAdapter.ContactHolder) list
										.getRecyclerView()
										.findViewHolderForAdapterPosition(
												adapter.findItemPosition(item));
						Pair<View, String> avatar =
								Pair.create((View) holder.avatar, ViewCompat
										.getTransitionName(holder.avatar));
						Pair<View, String> bulb =
								Pair.create((View) holder.bulb, ViewCompat
										.getTransitionName(holder.bulb));
						ActivityOptionsCompat options =
								makeSceneTransitionAnimation(getActivity(),
										avatar, bulb);
						ActivityCompat.startActivity(getActivity(), i,
								options.toBundle());
					}
				};

		adapter = new ContactListAdapter(getContext(), onItemClickListener);
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
						KeyAgreementActivity.class));
			}
		});

		return contentView;
	}


	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadContacts();
	}

	@Override
	public void onPause() {
		super.onPause();
		adapter.clear();
		eventBus.removeListener(this);
	}

	private void loadContacts() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts = new ArrayList<>();
					for (Contact c : contactManager.getActiveContacts()) {
						try {
							ContactId id = c.getId();
							GroupId groupId =
									messagingManager.getConversationId(id);
							Collection<ConversationItem> messages =
									getMessages(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							LocalAuthor localAuthor = identityManager
									.getLocalAuthor(c.getLocalAuthorId());
							contacts.add(new ContactListItem(c, localAuthor,
									connected, groupId, messages));
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
			@Override
			public void run() {
				if (contacts.size() == 0) list.showData();
				else adapter.addAll(contacts);
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			if (((ContactAddedEvent) e).isActive()) {
				LOG.info("Contact added as active, reloading");
				loadContacts();
			}
		} else if (e instanceof ContactStatusChangedEvent) {
			LOG.info("Contact Status changed, reloading");
			loadContacts();
		} else if (e instanceof ContactConnectedEvent) {
			setConnected(((ContactConnectedEvent) e).getContactId(), true);
		} else if (e instanceof ContactDisconnectedEvent) {
			setConnected(((ContactDisconnectedEvent) e).getContactId(), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed");
			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof PrivateMessageReceivedEvent) {
			LOG.info("Message received, update contact");
			PrivateMessageReceivedEvent p = (PrivateMessageReceivedEvent) e;
			PrivateMessageHeader h = p.getMessageHeader();
			updateItem(p.getGroupId(), ConversationItem.from(h));
		} else if (e instanceof MessageStateChangedEvent) {
			MessageStateChangedEvent m = (MessageStateChangedEvent) e;
			ClientId c = m.getClientId();
			if (m.getState() == DELIVERED &&
					(c.equals(introductionManager.getClientId()) ||
							c.equals(forumSharingManager.getClientId()))) {
				LOG.info("Message added, reloading");
				reloadConversation(m.getMessage().getGroupId());
			}
		}
	}

	private void reloadConversation(final GroupId g) {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					ContactId c = messagingManager.getContactId(g);
					Collection<ConversationItem> messages =
							getMessages(c);
					updateItem(c, messages);
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
			final Collection<ConversationItem> messages) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItem(position);
				if (item != null) {
					item.setMessages(messages);
					adapter.updateItem(position, item);
				}
			}
		});
	}

	private void updateItem(final GroupId g, final ConversationItem m) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int position = adapter.findItemPosition(g);
				ContactListItem item = adapter.getItem(position);
				if (item != null) {
					item.addMessage(m);
					adapter.updateItem(position, item);
				}
			}
		});
	}

	private void removeItem(final ContactId c) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItem(position);
				if (item != null) adapter.remove(item);
			}
		});
	}

	private void setConnected(final ContactId c, final boolean connected) {
		listener.runOnUiThread(new Runnable() {
			@Override
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

	// This needs to be called from the DB thread
	private Collection<ConversationItem> getMessages(ContactId id)
			throws DbException {

		long now = System.currentTimeMillis();

		Collection<ConversationItem> messages = new ArrayList<>();

		Collection<PrivateMessageHeader> headers =
				messagingManager.getMessageHeaders(id);
		for (PrivateMessageHeader h : headers) {
			messages.add(ConversationItem.from(h));
		}
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading message headers took " + duration + " ms");

		now = System.currentTimeMillis();
		Collection<IntroductionMessage> introductions =
				introductionManager
						.getIntroductionMessages(id);
		for (IntroductionMessage m : introductions) {
			messages.add(ConversationItem.from(m));
		}
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading introduction messages took " + duration + " ms");

		now = System.currentTimeMillis();
		Collection<ForumInvitationMessage> invitations =
				forumSharingManager.getForumInvitationMessages(id);
		for (ForumInvitationMessage i : invitations) {
			messages.add(ConversationItem.from(i));
		}
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading forum invitations took " + duration + " ms");

		return messages;
	}
}
