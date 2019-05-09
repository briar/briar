package org.briarproject.briar.android.keyagreement;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.contact.event.ContactExchangeFailedEvent;
import org.briarproject.bramble.api.contact.event.ContactExchangeSucceededEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

@NotNullByDefault
class ContactExchangeViewModel extends AndroidViewModel
		implements EventListener {

	private final Executor ioExecutor;
	private final ContactExchangeManager contactExchangeManager;
	private final EventBus eventBus;
	private final MutableLiveData<Boolean> succeeded = new MutableLiveData<>();

	@Nullable
	private Author remoteAuthor, duplicateAuthor;

	@Inject
	ContactExchangeViewModel(Application app, @IoExecutor Executor ioExecutor,
			ContactExchangeManager contactExchangeManager, EventBus eventBus) {
		super(app);
		this.ioExecutor = ioExecutor;
		this.contactExchangeManager = contactExchangeManager;
		this.eventBus = eventBus;
		eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	@UiThread
	void startContactExchange(TransportId t, DuplexTransportConnection conn,
			SecretKey masterKey, boolean alice) {
		ioExecutor.execute(() -> contactExchangeManager.exchangeContacts(t,
				conn, masterKey, alice));
	}

	@UiThread
	@Nullable
	Author getRemoteAuthor() {
		return remoteAuthor;
	}

	@UiThread
	@Nullable
	Author getDuplicateAuthor() {
		return duplicateAuthor;
	}

	LiveData<Boolean> getSucceeded() {
		return succeeded;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactExchangeSucceededEvent) {
			ContactExchangeSucceededEvent c = (ContactExchangeSucceededEvent) e;
			remoteAuthor = c.getRemoteAuthor();
			succeeded.setValue(true);
		} else if (e instanceof ContactExchangeFailedEvent) {
			ContactExchangeFailedEvent c = (ContactExchangeFailedEvent) e;
			if (c.wasDuplicateContact())
				duplicateAuthor = requireNonNull(c.getDuplicateRemoteAuthor());
			succeeded.setValue(false);
		}
	}
}
