package org.briarproject.briar.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.view.BriarRecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

abstract class SharingStatusActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(SharingStatusActivity.class.getName());

	private GroupId groupId;
	private BriarRecyclerView sharedByList, sharedWithList;
	private SharingStatusAdapter sharedByAdapter, sharedWithAdapter;

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
	public void onStart() {
		super.onStart();
		loadSharedBy();
		loadSharedWith();
	}

	@Override
	public void onStop() {
		super.onStop();
		sharedByAdapter.clear();
		sharedByList.showProgressBar();
		sharedWithAdapter.clear();
		sharedWithList.showProgressBar();
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
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					List<ContactItem> contactItems = new ArrayList<>();
					for (Contact c : getSharedBy()) {
						ContactItem item = new ContactItem(c);
						contactItems.add(item);
					}
					displaySharedBy(contactItems);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySharedBy(final List<ContactItem> contacts) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) sharedByList.showData();
				else sharedByAdapter.addAll(contacts);
			}
		});
	}

	private void loadSharedWith() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					List<ContactItem> contactItems = new ArrayList<>();
					for (Contact c : getSharedWith()) {
						ContactItem item = new ContactItem(c);
						contactItems.add(item);
					}
					displaySharedWith(contactItems);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySharedWith(final List<ContactItem> contacts) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (contacts.isEmpty()) sharedWithList.showData();
				else sharedWithAdapter.addAll(contacts);
			}
		});
	}

}
