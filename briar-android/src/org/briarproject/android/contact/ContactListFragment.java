package org.briarproject.android.contact;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.InvitationRequestReceivedEvent;
import org.briarproject.api.event.InvitationResponseReceivedEvent;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.BriarActivity.GROUP_ID;

public class ContactListFragment extends BaseFragment implements EventListener {

	public static final String TAG = ContactListFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ConnectionRegistry connectionRegistry;
	@Inject
	EventBus eventBus;
	@Inject
	AndroidNotificationManager notificationManager;

	private ContactListAdapter adapter;
	private BriarRecyclerView list;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile IdentityManager identityManager;
	@Inject
	volatile ConversationManager conversationManager;

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

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView =
				inflater.inflate(R.layout.list, container,
						false);

		BaseContactListAdapter.OnItemClickListener onItemClickListener =
				new ContactListAdapter.OnItemClickListener() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						GroupId groupId = item.getGroupId();
						Intent i = new Intent(getActivity(),
								ConversationActivity.class);
						i.putExtra(GROUP_ID, groupId.getBytes());

						// work-around for android bug #224270
						if (Build.VERSION.SDK_INT >= 23) {
							ContactListAdapter.ContactHolder holder =
									(ContactListAdapter.ContactHolder) list
											.getRecyclerView()
											.findViewHolderForAdapterPosition(
													adapter.findItemPosition(
															item));
							Pair<View, String> avatar =
									Pair.create((View) holder.avatar, ViewCompat
											.getTransitionName(holder.avatar));
							Pair<View, String> bulb =
									Pair.create((View) holder.bulb, ViewCompat.
											getTransitionName(holder.bulb));
							ActivityOptionsCompat options =
									makeSceneTransitionAnimation(getActivity(),
											avatar, bulb);
							ActivityCompat.startActivity(getActivity(), i,
									options.toBundle());
						} else {
							getActivity().startActivity(i);
						}
					}
				};

		adapter = new ContactListAdapter(getContext(), onItemClickListener);
		list = (BriarRecyclerView) contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getContext()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));

		return contentView;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.contact_list_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_add_contact:
				Intent intent =
						new Intent(getContext(), KeyAgreementActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		notificationManager.blockAllContactNotifications();
		notificationManager.clearAllContactNotifications();
		eventBus.addListener(this);
		loadContacts();
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		notificationManager.unblockAllContactNotifications();
		adapter.clear();
		list.showProgressBar();
		list.stopPeriodicUpdate();
	}

	private void loadContacts() {
		final int revision = adapter.getRevision();
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
									conversationManager.getConversationId(id);
							GroupCount count =
									conversationManager.getGroupCount(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							LocalAuthor localAuthor = identityManager
									.getLocalAuthor(c.getLocalAuthorId());
							contacts.add(new ContactListItem(c, localAuthor,
									connected, groupId, count));
						} catch (NoSuchContactException e) {
							// Continue
						}
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					displayContacts(revision, contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final int revision,
			final List<ContactListItem> contacts) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (revision == adapter.getRevision()) {
					adapter.incrementRevision();
					if (contacts.isEmpty()) list.showData();
					else adapter.addAll(contacts);
				} else {
					LOG.info("Concurrent update, reloading");
					loadContacts();
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactStatusChangedEvent) {
			ContactStatusChangedEvent c = (ContactStatusChangedEvent) e;
			if (c.isActive()) {
				LOG.info("Contact activated, reloading");
				loadContacts();
			} else {
				LOG.info("Contact deactivated, removing item");
				removeItem(c.getContactId());
			}
		} else if (e instanceof ContactConnectedEvent) {
			setConnected(((ContactConnectedEvent) e).getContactId(), true);
		} else if (e instanceof ContactDisconnectedEvent) {
			setConnected(((ContactDisconnectedEvent) e).getContactId(), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, removing item");
			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof PrivateMessageReceivedEvent) {
			LOG.info("Private message received, updating item");
			PrivateMessageReceivedEvent p = (PrivateMessageReceivedEvent) e;
			PrivateMessageHeader h = p.getMessageHeader();
			updateItem(p.getContactId(), ConversationItem.from(h));
		} else if (e instanceof IntroductionRequestReceivedEvent) {
			LOG.info("Introduction request received, updating item");
			IntroductionRequestReceivedEvent m =
					(IntroductionRequestReceivedEvent) e;
			IntroductionRequest ir = m.getIntroductionRequest();
			updateItem(m.getContactId(),
					ConversationItem.from(getContext(), "", ir));
		} else if (e instanceof IntroductionResponseReceivedEvent) {
			LOG.info("Introduction response received, updating item");
			IntroductionResponseReceivedEvent m =
					(IntroductionResponseReceivedEvent) e;
			IntroductionResponse ir = m.getIntroductionResponse();
			updateItem(m.getContactId(),
					ConversationItem.from(getContext(), "", ir));
		} else if (e instanceof InvitationRequestReceivedEvent) {
			LOG.info("Invitation request received, updating item");
			InvitationRequestReceivedEvent m = (InvitationRequestReceivedEvent) e;
			InvitationRequest ir = m.getRequest();
			updateItem(m.getContactId(),
					ConversationItem.from(getContext(), "", ir));
		} else if (e instanceof InvitationResponseReceivedEvent) {
			LOG.info("Invitation response received, updating item");
			InvitationResponseReceivedEvent m =
					(InvitationResponseReceivedEvent) e;
			InvitationResponse ir = m.getResponse();
			updateItem(m.getContactId(),
					ConversationItem.from(getContext(), "", ir));
		}
	}

	private void updateItem(final ContactId c, final ConversationItem m) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.incrementRevision();
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItemAt(position);
				if (item != null) {
					item.addMessage(m);
					adapter.updateItemAt(position, item);
				}
			}
		});
	}

	private void removeItem(final ContactId c) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.incrementRevision();
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItemAt(position);
				if (item != null) adapter.remove(item);
			}
		});
	}

	private void setConnected(final ContactId c, final boolean connected) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.incrementRevision();
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItemAt(position);
				if (item != null) {
					item.setConnected(connected);
					adapter.notifyItemChanged(position);
				}
			}
		});
	}

}
