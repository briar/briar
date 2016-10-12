package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.sharing.ShareActivity.CONTACTS;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIds;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIntegers;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

public class ContactSelectorFragment extends BaseFragment implements
		BaseContactListAdapter.OnItemClickListener {

	public static final String TAG = ContactSelectorFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	private ShareActivity shareActivity;
	private Menu menu;
	private BriarRecyclerView list;
	private ContactSelectorAdapter adapter;
	private Collection<ContactId> selectedContacts;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile IdentityManager identityManager;
	@Inject
	volatile ForumSharingManager forumSharingManager;

	private volatile GroupId groupId;

	public static ContactSelectorFragment newInstance(GroupId groupId) {

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		ContactSelectorFragment fragment = new ContactSelectorFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		shareActivity = (ShareActivity) context;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView = inflater.inflate(R.layout.list, container, false);

		if (Build.VERSION.SDK_INT >= 21) {
			setExitTransition(new Fade());
		}

		adapter = new ContactSelectorAdapter(getActivity(), this);

		list = (BriarRecyclerView) contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts_selector));

		// restore selected contacts if available
		if (savedInstanceState != null) {
			ArrayList<Integer> intContacts =
					savedInstanceState.getIntegerArrayList(CONTACTS);
			if (intContacts != null) {
				selectedContacts = getContactsFromIntegers(intContacts);
			}
		}

		return contentView;
	}

	@Override
	public void onStart() {
		super.onStart();
		loadContacts(selectedContacts);
	}

	@Override
	public void onStop() {
		super.onStop();
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (adapter != null) {
			selectedContacts = adapter.getSelectedContactIds();
			outState.putIntegerArrayList(CONTACTS,
					getContactsFromIds(selectedContacts));
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.forum_share_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
		this.menu = menu;
		// hide sharing action initially, if no contact is selected
		updateMenuItem();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				shareActivity.onBackPressed();
				return true;
			case R.id.action_share_forum:
				selectedContacts = adapter.getSelectedContactIds();
				shareActivity.showMessageScreen(groupId, selectedContacts);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
		((SelectableContactListItem) item).toggleSelected();
		adapter.notifyItemChanged(adapter.findItemPosition(item), item);

		updateMenuItem();
	}

	private void loadContacts(@Nullable final Collection<ContactId> selection) {
		shareActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts = new ArrayList<>();

					for (Contact c : contactManager.getActiveContacts()) {
						LocalAuthor localAuthor = identityManager
								.getLocalAuthor(c.getLocalAuthorId());
						// was this contact already selected?
						boolean selected = selection != null &&
								selection.contains(c.getId());
						// do we have already some sharing with that contact?
						boolean disabled = shareActivity.isDisabled(groupId, c);
						contacts.add(new SelectableContactListItem(c,
								localAuthor, groupId, selected, disabled));
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts(contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final List<ContactListItem> contacts) {
		shareActivity.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) list.showData();
				else adapter.addAll(contacts);
				updateMenuItem();
			}
		});
	}

	private void updateMenuItem() {
		if (menu == null) return;
		MenuItem item = menu.findItem(R.id.action_share_forum);
		if (item == null) return;

		selectedContacts = adapter.getSelectedContactIds();
		if (selectedContacts.size() > 0) {
			item.setVisible(true);
		} else {
			item.setVisible(false);
		}
	}
}
