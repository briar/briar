package org.briarproject.briar.android.contact.add.remote;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedRemotelyEvent;
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class PendingContactListViewModel extends AndroidViewModel
		implements EventListener {

	private final Logger LOG =
			getLogger(PendingContactListViewModel.class.getName());

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final ContactManager contactManager;
	private final EventBus eventBus;

	private final MutableLiveData<Collection<PendingContact>> pendingContacts =
			new MutableLiveData<>();

	@Inject
	public PendingContactListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			ContactManager contactManager, EventBus eventBus) {
		super(application);
		this.dbExecutor = dbExecutor;
		this.contactManager = contactManager;
		this.eventBus = eventBus;
		this.eventBus.addListener(this);
		loadPendingContacts();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedRemotelyEvent ||
				e instanceof PendingContactStateChangedEvent) {
			loadPendingContacts();
		}
	}

	private void loadPendingContacts() {
		dbExecutor.execute(() -> {
			try {
				pendingContacts.postValue(contactManager.getPendingContacts());
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<Collection<PendingContact>> getPendingContacts() {
		return pendingContacts;
	}

	void removePendingContact(PendingContactId id, Runnable commitAction) {
		dbExecutor.execute(() -> {
			try {
				contactManager
						.removePendingContact(id, commitAction);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
		loadPendingContacts();
	}

}
