package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

abstract class SharingStatusActivity extends BriarActivity {

	private GroupId groupId;
	private BriarRecyclerView sharedByList, sharedWithList;
	private SharingStatusAdapter sharedByAdapter, sharedWithAdapter;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;

	public final static String TAG = SharingStatusActivity.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_sharing_status);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);

		sharedByList = (BriarRecyclerView) findViewById(R.id.sharedByView);
		sharedByAdapter = new SharingStatusAdapter(this);
		sharedByList.setLayoutManager(new LinearLayoutManager(this));
		sharedByList.setAdapter(sharedByAdapter);
		sharedByList.setEmptyText(getString(R.string.nobody));

		sharedWithList = (BriarRecyclerView) findViewById(R.id.sharedWithView);
		sharedWithAdapter = new SharingStatusAdapter(this);
		sharedWithList.setLayoutManager(new LinearLayoutManager(this));
		sharedWithList.setAdapter(sharedWithAdapter);
		sharedWithList.setEmptyText(getString(R.string.nobody));
	}

	@Override
	public void onResume() {
		super.onResume();

		loadSharedBy();
		loadSharedWith();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * This must only be called from the DbThread
	 */
	abstract protected Collection<Contact> getSharedWith() throws DbException;

	/**
	 * This must only be called from the DbThread
	 */
	abstract protected Collection<Contact> getSharedBy() throws DbException;

	protected GroupId getGroupId() {
		return groupId;
	}

	private void loadSharedBy() {
		dbController.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				List<ContactListItem> contactItems = new ArrayList<>();
				try {
					for (Contact c : getSharedBy()) {
						LocalAuthor localAuthor = identityManager
								.getLocalAuthor(c.getLocalAuthorId());
						ContactListItem item =
								new ContactListItem(c, localAuthor, false, null,
										null);
						contactItems.add(item);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				displaySharedBy(contactItems);
			}
		});
	}

	private void displaySharedBy(final List<ContactListItem> contacts) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) {
					sharedByList.showData();
				} else {
					sharedByAdapter.addAll(contacts);
				}
			}
		});
	}

	private void loadSharedWith() {
		dbController.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				List<ContactListItem> contactItems = new ArrayList<>();
				try {
					for (Contact c : getSharedWith()) {
						LocalAuthor localAuthor = identityManager
								.getLocalAuthor(c.getLocalAuthorId());
						ContactListItem item =
								new ContactListItem(c, localAuthor, false, null,
										null);
						contactItems.add(item);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				displaySharedWith(contactItems);
			}
		});
	}

	private void displaySharedWith(final List<ContactListItem> contacts) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) {
					sharedWithList.showData();
				} else {
					sharedWithAdapter.addAll(contacts);
				}
			}
		});
	}

}
