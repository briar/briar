package org.briarproject.briar.android.contact;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.MessagingManager.PendingContact;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class PendingRequestsActivity extends BriarActivity
		implements EventListener {

	@Inject
	MessagingManager messagingManager;
	@Inject
	ContactManager contactManager;
	@Inject
	EventBus eventBus;

	private PendingRequestsAdapter adapter;
	private BriarRecyclerView list;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.list);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		adapter = new PendingRequestsAdapter(this, PendingContact.class);
		list = findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		runOnDbThread(() -> {
			try {
				Collection<PendingContact> contacts =
						messagingManager.getPendingContacts();
				addPendingContacts(contacts);
			} catch (DbException e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		adapter.clear();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			runOnDbThread(() -> {
				try {
					Contact contact = contactManager
							.getContact(((ContactAddedEvent) e).getContactId());
					runOnUiThreadUnlessDestroyed(() -> {
						adapter.remove(contact);
						if (adapter.isEmpty()) finish();
					});
				} catch (DbException e1) {
					e1.printStackTrace();
				}
			});
		}
	}

	private void addPendingContacts(Collection<PendingContact> contacts) {
		runOnUiThreadUnlessDestroyed(() -> {
			if (contacts.isEmpty()) {
				list.showData();
			} else {
				adapter.addAll(contacts);
			}
		});
	}

}
