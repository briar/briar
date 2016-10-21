package org.briarproject.android.introduction;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.ContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
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
	private ContactChooserAdapter adapter;
	private ContactId contactId;

	// Fields that are accessed from background threads must be volatile
	volatile Contact c1;
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile IdentityManager identityManager;
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

		ContactListAdapter.OnItemClickListener onItemClickListener =
				new ContactListAdapter.OnItemClickListener() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						if (c1 == null) throw new IllegalStateException();
						Contact c2 = item.getContact();
						if (!c1.getLocalAuthorId()
								.equals(c2.getLocalAuthorId())) {
							warnAboutDifferentIdentities(view, c1, c2);
						} else {
							introductionActivity.showMessageScreen(view, c1,
									c2);
						}
					}
				};
		adapter = new ContactChooserAdapter(getActivity(), onItemClickListener);

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
					AuthorId localAuthorId =
							identityManager.getLocalAuthor().getId();
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
							LocalAuthor localAuthor = identityManager
									.getLocalAuthor(c.getLocalAuthorId());
							contacts.add(new ContactListItem(c, localAuthor,
									connected, groupId, count));
						}
					}
					displayContacts(localAuthorId, contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final AuthorId localAuthorId,
			final List<ContactListItem> contacts) {
		introductionActivity.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				adapter.setLocalAuthor(localAuthorId);
				if (contacts.isEmpty()) list.showData();
				else adapter.addAll(contacts);
			}
		});
	}

	private void warnAboutDifferentIdentities(final View view, final Contact c1,
			final Contact c2) {

		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						introductionActivity.showMessageScreen(view, c1, c2);
					}
				};
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
				R.style.BriarDialogTheme);
		builder.setTitle(getString(
				R.string.introduction_warn_different_identities_title));
		builder.setMessage(getString(
				R.string.introduction_warn_different_identities_text));
		builder.setPositiveButton(R.string.dialog_button_introduce, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

}
