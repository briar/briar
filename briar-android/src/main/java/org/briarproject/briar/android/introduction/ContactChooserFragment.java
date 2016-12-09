package org.briarproject.briar.android.introduction;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.briar.android.contact.ContactListAdapter;
import org.briarproject.briar.android.contact.ContactListItem;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.messaging.ConversationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.contact.ConversationActivity.CONTACT_ID;

public class ContactChooserFragment extends BaseFragment {

	public static final String TAG = ContactChooserFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	private BriarRecyclerView list;
	private ContactListAdapter adapter;
	private ContactId contactId;

	// Fields that are accessed from background threads must be volatile
	volatile Contact c1;
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile ConversationManager conversationManager;
	@Inject
	volatile ConnectionRegistry connectionRegistry;

	public static ContactChooserFragment newInstance(ContactId id) {

		Bundle args = new Bundle();

		ContactChooserFragment fragment = new ContactChooserFragment();
		args.putInt(CONTACT_ID, id.getInt());
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView = inflater.inflate(R.layout.list, container, false);

		OnContactClickListener<ContactListItem> onContactClickListener =
				new OnContactClickListener<ContactListItem>() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						if (c1 == null) throw new IllegalStateException();
						Contact c2 = item.getContact();
						showMessageScreen(c1, c2);
					}
				};
		adapter = new ContactListAdapter(getActivity(), onContactClickListener);

		list = (BriarRecyclerView) contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));

		contactId = new ContactId(getArguments().getInt(CONTACT_ID));

		return contentView;
	}

	@Override
	public void onStart() {
		super.onStart();
		loadContacts();
	}

	@Override
	public void onStop() {
		super.onStop();
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private void loadContacts() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					List<ContactListItem> contacts = new ArrayList<>();
					for (Contact c : contactManager.getActiveContacts()) {
						if (c.getId().equals(contactId)) {
							c1 = c;
						} else {
							ContactId id = c.getId();
							GroupCount count =
									conversationManager.getGroupCount(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							contacts.add(new ContactListItem(c, connected,
									count));
						}
					}
					displayContacts(contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final List<ContactListItem> contacts) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) list.showData();
				else adapter.addAll(contacts);
			}
		});
	}

	private void showMessageScreen(Contact c1, Contact c2) {
		IntroductionMessageFragment messageFragment =
				IntroductionMessageFragment
						.newInstance(c1.getId().getInt(), c2.getId().getInt());
		showNextFragment(messageFragment);
	}

}
