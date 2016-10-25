package org.briarproject.android.introduction;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.android.contact.ContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class ContactChooserFragment extends BaseFragment {

	public static final String TAG = ContactChooserFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	private IntroductionActivity introductionActivity;
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

	public static ContactChooserFragment newInstance() {
		
		Bundle args = new Bundle();
		
		ContactChooserFragment fragment = new ContactChooserFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		introductionActivity = (IntroductionActivity) context;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView = inflater.inflate(R.layout.list, container, false);

		if (Build.VERSION.SDK_INT >= 21) {
			setExitTransition(new Fade());
		}

		OnContactClickListener<ContactListItem> onContactClickListener =
				new OnContactClickListener<ContactListItem>() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						if (c1 == null) throw new IllegalStateException();
						Contact c2 = item.getContact();
						introductionActivity.showMessageScreen(view, c1, c2);
					}
				};
		adapter = new ContactListAdapter(getActivity(), onContactClickListener);

		list = (BriarRecyclerView) contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));

		contactId = introductionActivity.getContactId();

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
		introductionActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					List<ContactListItem> contacts = new ArrayList<>();
					for (Contact c : contactManager.getActiveContacts()) {
						if (c.getId().equals(contactId)) {
							c1 = c;
						} else {
							ContactId id = c.getId();
							GroupId groupId =
									conversationManager.getConversationId(id);
							GroupCount count =
									conversationManager.getGroupCount(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							contacts.add(new ContactListItem(c, connected,
									groupId, count));
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
		introductionActivity.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) list.showData();
				else adapter.addAll(contacts);
			}
		});
	}

}
