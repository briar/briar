package org.briarproject.briar.android.remotewipe;

import android.app.Application;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.sync.event.MessagesSentEvent;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class RemoteWipeActivatedViewModel extends AndroidViewModel implements
		EventListener {
	private final RemoteWipeManager remoteWipeManager;
	private final DatabaseComponent db;
	private final MutableLiveEvent<Boolean> confirmSent =
			new MutableLiveEvent<>();
	private int numberOfConfirmMessages;
	private int messagesSent = 0;

	@Inject
	RemoteWipeActivatedViewModel(
			@NonNull Application application,
			RemoteWipeManager remoteWipeManager,
			DatabaseComponent db, EventBus eventBus) {
		super(application);
		this.remoteWipeManager = remoteWipeManager;
		this.db = db;

		eventBus.addListener(this);
	}

	public void sendConfirmMessages() {
		try {
			numberOfConfirmMessages = db.transactionWithResult(false,
					remoteWipeManager::sendConfirmMessages);
		} catch (DbException | FormatException e) {
			// If there is a problem sending the messages, just wipe
			confirmSent.postEvent(true);
		}
	}

	public LiveEvent<Boolean> getConfirmSent() {
		return confirmSent;
	}

	@Override
	public void eventOccurred(Event e) {
		// As soon as we know the confirm messages are sent, we can wipe
		if (e instanceof MessagesSentEvent) {
			messagesSent++;
			if (messagesSent >= numberOfConfirmMessages) {
				confirmSent.postEvent(true);
			}
		}
	}
}

