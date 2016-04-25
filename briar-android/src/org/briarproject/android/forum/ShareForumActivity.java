package org.briarproject.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.util.BriarRecyclerView;
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
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.ForumActivity.FORUM_NAME;

public class ShareForumActivity extends BriarActivity implements
		BaseContactListAdapter.OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ShareForumActivity.class.getName());

	private ContactSelectorAdapter adapter;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile IdentityManager identityManager;
	@Inject protected volatile ContactManager contactManager;
	@Inject protected volatile ForumSharingManager forumSharingManager;
	private volatile GroupId groupId;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.introduction_contact_chooser);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra(FORUM_NAME);
		if (forumName == null) throw new IllegalStateException();
		setTitle(forumName);

		adapter = new ContactSelectorAdapter(this, this);
		BriarRecyclerView list =
				(BriarRecyclerView) findViewById(R.id.contactList);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));
	}

	@Override
	public void onResume() {
		super.onResume();

		loadContactsAndVisibility();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.forum_share_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_share_forum:
				storeVisibility();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	private void loadContactsAndVisibility() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts =
							new ArrayList<ContactListItem>();
					Collection<ContactId> selectedContacts =
							new HashSet<ContactId>(
									forumSharingManager.getSharedWith(groupId));

					for (Contact c : contactManager.getActiveContacts()) {
						LocalAuthor localAuthor = identityManager
								.getLocalAuthor(c.getLocalAuthorId());
						boolean selected = selectedContacts.contains(c.getId());
						contacts.add(
								new SelectableContactListItem(c, localAuthor,
										groupId, selected));
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

	private void displayContacts(final List<ContactListItem> contact) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.addAll(contact);
			}
		});
	}

	private void storeVisibility() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<ContactId> selected =
							adapter.getSelectedContactIds();
					forumSharingManager.setSharedWith(groupId, selected);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Update took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				finishOnUiThread();
			}
		});
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
		((SelectableContactListItem) item).toggleSelected();
		adapter.notifyItemChanged(adapter.findItemPosition(item), item);
	}

}
