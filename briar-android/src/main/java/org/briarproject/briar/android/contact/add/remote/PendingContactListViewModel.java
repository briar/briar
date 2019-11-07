package org.briarproject.briar.android.contact.add.remote;

import android.app.Application;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.PendingContactState;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.rendezvous.RendezvousPoller;
import org.briarproject.bramble.api.rendezvous.event.RendezvousPollEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.contact.PendingContactState.OFFLINE;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class PendingContactListViewModel extends AndroidViewModel
		implements EventListener {

	private final Logger LOG =
			getLogger(PendingContactListViewModel.class.getName());

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final ContactManager contactManager;
	private final RendezvousPoller rendezvousPoller;
	private final EventBus eventBus;

	private final MutableLiveData<Collection<PendingContactItem>>
			pendingContacts = new MutableLiveData<>();
	private final MutableLiveData<Boolean> hasInternetConnection =
			new MutableLiveData<>();

	@Inject
	PendingContactListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			ContactManager contactManager,
			RendezvousPoller rendezvousPoller,
			EventBus eventBus) {
		super(application);
		this.dbExecutor = dbExecutor;
		this.contactManager = contactManager;
		this.rendezvousPoller = rendezvousPoller;
		this.eventBus = eventBus;
		this.eventBus.addListener(this);
	}

	void onCreate() {
		if (pendingContacts.getValue() == null) loadPendingContacts();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof PendingContactStateChangedEvent ||
				e instanceof PendingContactRemovedEvent ||
				e instanceof RendezvousPollEvent) {
			loadPendingContacts();
		}
	}

	private void loadPendingContacts() {
		dbExecutor.execute(() -> {
			try {
				Collection<Pair<PendingContact, PendingContactState>> pairs =
						contactManager.getPendingContacts();
				List<PendingContactItem> items = new ArrayList<>(pairs.size());
				boolean online = items.isEmpty();
				for (Pair<PendingContact, PendingContactState> pair : pairs) {
					PendingContact p = pair.getFirst();
					PendingContactState state = pair.getSecond();
					long lastPoll = rendezvousPoller.getLastPollTime(p.getId());
					items.add(new PendingContactItem(p, state, lastPoll));
					online = online || state != OFFLINE;
				}
				pendingContacts.postValue(items);
				hasInternetConnection.postValue(online);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<Collection<PendingContactItem>> getPendingContacts() {
		return pendingContacts;
	}

	void removePendingContact(PendingContactId id) {
		dbExecutor.execute(() -> {
			try {
				contactManager.removePendingContact(id);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<Boolean> getHasInternetConnection() {
		return hasInternetConnection;
	}

}
