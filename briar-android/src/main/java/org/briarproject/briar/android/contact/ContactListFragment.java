package org.briarproject.briar.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
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
import org.briarproject.briar.android.contact.add.remote.AddContactActivity;
import org.briarproject.briar.android.contact.add.remote.PendingContactListActivity;
import org.briarproject.briar.android.conversation.ConversationActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.keyagreement.ContactExchangeActivity;
import org.briarproject.briar.android.util.BriarSnackbarBuilder;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import io.github.kobakei.materialfabspeeddial.FabSpeedDial;
import io.github.kobakei.materialfabspeeddial.FabSpeedDial.OnMenuItemClickListener;

import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static androidx.core.view.ViewCompat.getTransitionName;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.android.util.UiUtils.isSamsung7;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactListFragment extends BaseFragment implements EventListener,
		OnMenuItemClickListener {

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
	/**
	 * The Snackbar is non-null when shown and null otherwise.
	 * Use {@link #showSnackBar()} and {@link #dismissSnackBar()} to interact.
	 */
	@Nullable
	private Snackbar snackbar = null;

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
		requireNonNull(getActivity()).setTitle(R.string.contact_list_button);

		View contentView = inflater.inflate(R.layout.fragment_contact_list,
				container, false);

		FabSpeedDial speedDial = contentView.findViewById(R.id.speedDial);
		speedDial.addOnMenuItemClickListener(this);

		OnContactClickListener<ContactListItem> onContactClickListener =
				(view, item) -> {
					Intent i = new Intent(getActivity(),
							ConversationActivity.class);
					ContactId contactId = item.getContact().getId();
					i.putExtra(CONTACT_ID, contactId.getInt());

					if (SDK_INT >= 23 && !isSamsung7()) {
						ContactListItemViewHolder holder =
								(ContactListItemViewHolder) list
										.getRecyclerView()
										.findViewHolderForAdapterPosition(
												adapter.findItemPosition(item));
						Pair<View, String> avatar =
								Pair.create(holder.avatar,
										getTransitionName(holder.avatar));
						Pair<View, String> bulb =
								Pair.create(holder.bulb,
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
				};
		adapter = new ContactListAdapter(requireContext(),
				onContactClickListener);
		list = contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		list.setAdapter(adapter);
		list.setEmptyImage(R.drawable.ic_empty_state_contact_list);
		list.setEmptyText(getString(R.string.no_contacts));
		list.setEmptyAction(getString(R.string.no_contacts_action));

		return contentView;
	}

	@Override
	public void onMenuItemClick(FloatingActionButton fab, @Nullable TextView v,
			int itemId) {
		switch (itemId) {
			case R.id.action_add_contact_nearby:
				Intent intent =
						new Intent(getContext(), ContactExchangeActivity.class);
				startActivity(intent);
				return;
			case R.id.action_add_contact_remotely:
				startActivity(
						new Intent(getContext(), AddContactActivity.class));
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		notificationManager.clearAllContactNotifications();
		notificationManager.clearAllContactAddedNotifications();
		loadContacts();
		checkForPendingContacts();
		list.startPeriodicUpdate();
	}

	private void checkForPendingContacts() {
		listener.runOnDbThread(() -> {
			try {
				if (contactManager.getPendingContacts().isEmpty()) {
					runOnUiThreadUnlessDestroyed(this::dismissSnackBar);
				} else {
					runOnUiThreadUnlessDestroyed(this::showSnackBar);
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		adapter.clear();
		list.showProgressBar();
		list.stopPeriodicUpdate();
		dismissSnackBar();
	}

	private void loadContacts() {
		int revision = adapter.getRevision();
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				List<ContactListItem> contacts = new ArrayList<>();
				for (Contact c : contactManager.getContacts()) {
					try {
						ContactId id = c.getId();
						GroupCount count =
								conversationManager.getGroupCount(id);
						boolean connected =
								connectionRegistry.isConnected(c.getId());
						contacts.add(new ContactListItem(c, connected, count));
					} catch (NoSuchContactException e) {
						// Continue
					}
				}
				logDuration(LOG, "Full load", start);
				displayContacts(revision, contacts);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displayContacts(int revision, List<ContactListItem> contacts) {
		runOnUiThreadUnlessDestroyed(() -> {
			if (revision == adapter.getRevision()) {
				adapter.incrementRevision();
				if (contacts.isEmpty()) list.showData();
				else adapter.replaceAll(contacts);
			} else {
				LOG.info("Concurrent update, reloading");
				loadContacts();
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			LOG.info("Contact added, reloading");
			loadContacts();
		} else if (e instanceof ContactConnectedEvent) {
			setConnected(((ContactConnectedEvent) e).getContactId(), true);
		} else if (e instanceof ContactDisconnectedEvent) {
			setConnected(((ContactDisconnectedEvent) e).getContactId(), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, removing item");
			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof ConversationMessageReceivedEvent) {
			LOG.info("Conversation message received, updating item");
			ConversationMessageReceivedEvent p =
					(ConversationMessageReceivedEvent) e;
			ConversationMessageHeader h = p.getMessageHeader();
			updateItem(p.getContactId(), h);
		} else if (e instanceof PendingContactAddedEvent ||
				e instanceof PendingContactRemovedEvent) {
			checkForPendingContacts();
		}
	}

	@UiThread
	private void updateItem(ContactId c, ConversationMessageHeader h) {
		adapter.incrementRevision();
		int position = adapter.findItemPosition(c);
		ContactListItem item = adapter.getItemAt(position);
		if (item != null) {
			item.addMessage(h);
			adapter.updateItemAt(position, item);
		}
	}

	@UiThread
	private void removeItem(ContactId c) {
		adapter.incrementRevision();
		int position = adapter.findItemPosition(c);
		ContactListItem item = adapter.getItemAt(position);
		if (item != null) adapter.remove(item);
	}

	@UiThread
	private void setConnected(ContactId c, boolean connected) {
		adapter.incrementRevision();
		int position = adapter.findItemPosition(c);
		ContactListItem item = adapter.getItemAt(position);
		if (item != null) {
			item.setConnected(connected);
			adapter.updateItemAt(position, item);
		}
	}

	@UiThread
	private void showSnackBar() {
		if (snackbar != null) return;
		View v = requireNonNull(getView());
		int stringRes = R.string.pending_contact_requests_snackbar;
		snackbar = new BriarSnackbarBuilder()
				.setAction(R.string.show, view -> showPendingContactList())
				.make(v, stringRes, LENGTH_INDEFINITE);
		snackbar.show();
	}

	@UiThread
	private void dismissSnackBar() {
		if (snackbar == null) return;
		snackbar.dismiss();
		snackbar = null;
	}

	private void showPendingContactList() {
		Intent i = new Intent(getContext(), PendingContactListActivity.class);
		startActivity(i);
	}

}
