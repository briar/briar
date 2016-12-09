package org.briarproject.briar.android.contact;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.ContactStatusChangedEvent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.keyagreement.KeyAgreementActivity;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.BaseMessageHeader;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.api.messaging.ConversationManager;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static android.support.v4.view.ViewCompat.getTransitionName;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.contact.ConversationActivity.CONTACT_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
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
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle(R.string.contact_list_button);

		View contentView = inflater.inflate(R.layout.list, container, false);

		OnContactClickListener<ContactListItem> onContactClickListener =
				new OnContactClickListener<ContactListItem>() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						Intent i = new Intent(getActivity(),
								ConversationActivity.class);
						ContactId contactId = item.getContact().getId();
						i.putExtra(CONTACT_ID, contactId.getInt());

						if (Build.VERSION.SDK_INT >= 23) {
							ContactListItemViewHolder holder =
									(ContactListItemViewHolder) list
											.getRecyclerView()
											.findViewHolderForAdapterPosition(
													adapter.findItemPosition(
															item));
							Pair<View, String> avatar =
									Pair.create((View) holder.avatar,
											getTransitionName(holder.avatar));
							Pair<View, String> bulb =
									Pair.create((View) holder.bulb,
											getTransitionName(holder.bulb));
							ActivityOptionsCompat options =
									makeSceneTransitionAnimation(getActivity(),
											avatar, bulb);
							ActivityCompat.startActivity(getActivity(), i,
									options.toBundle());
						} else {
							// work-around for android bug #224270
							startActivity(i);
						}
					}
				};
		adapter = new ContactListAdapter(getContext(), onContactClickListener);
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
							GroupCount count =
									conversationManager.getGroupCount(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							contacts.add(new ContactListItem(c, connected,
									count));
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
		runOnUiThreadUnlessDestroyed(new Runnable() {
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
			updateItem(p.getContactId(), h);
		} else if (e instanceof IntroductionRequestReceivedEvent) {
			LOG.info("Introduction request received, updating item");
			IntroductionRequestReceivedEvent m =
					(IntroductionRequestReceivedEvent) e;
			IntroductionRequest ir = m.getIntroductionRequest();
			updateItem(m.getContactId(), ir);
		} else if (e instanceof IntroductionResponseReceivedEvent) {
			LOG.info("Introduction response received, updating item");
			IntroductionResponseReceivedEvent m =
					(IntroductionResponseReceivedEvent) e;
			IntroductionResponse ir = m.getIntroductionResponse();
			updateItem(m.getContactId(), ir);
		} else if (e instanceof InvitationRequestReceivedEvent) {
			LOG.info("Invitation Request received, update item");
			InvitationRequestReceivedEvent m =
					(InvitationRequestReceivedEvent) e;
			InvitationRequest ir = m.getRequest();
			updateItem(m.getContactId(), ir);
		} else if (e instanceof InvitationResponseReceivedEvent) {
			LOG.info("Invitation response received, updating item");
			InvitationResponseReceivedEvent m =
					(InvitationResponseReceivedEvent) e;
			InvitationResponse ir = m.getResponse();
			updateItem(m.getContactId(), ir);
		}
	}

	private void updateItem(final ContactId c, final BaseMessageHeader h) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.incrementRevision();
				int position = adapter.findItemPosition(c);
				ContactListItem item = adapter.getItemAt(position);
				if (item != null) {
					ConversationItem i = ConversationItem.from(getContext(), h);
					item.addMessage(i);
					adapter.updateItemAt(position, item);
				}
			}
		});
	}

	private void removeItem(final ContactId c) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
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
		runOnUiThreadUnlessDestroyed(new Runnable() {
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
