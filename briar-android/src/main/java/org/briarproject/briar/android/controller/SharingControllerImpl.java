package org.briarproject.briar.android.controller;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.event.ConnectionStatusChangedEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;

import static org.briarproject.bramble.api.plugin.ConnectionStatus.CONNECTED;

@NotNullByDefault
public class SharingControllerImpl implements SharingController, EventListener {

	private final EventBus eventBus;
	private final ConnectionRegistry connectionRegistry;

	// UI thread
	private final Set<ContactId> contacts = new HashSet<>();

	// UI thread
	@Nullable
	private SharingListener listener;

	@Inject
	SharingControllerImpl(EventBus eventBus,
			ConnectionRegistry connectionRegistry) {
		this.eventBus = eventBus;
		this.connectionRegistry = connectionRegistry;
	}

	@Override
	public void setSharingListener(SharingListener listener) {
		this.listener = listener;
	}

	@Override
	public void unsetSharingListener(SharingListener listener) {
		if (this.listener == listener) this.listener = null;
	}

	@Override
	public void onStart() {
		eventBus.addListener(this);
	}

	@Override
	public void onStop() {
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ConnectionStatusChangedEvent) {
			ConnectionStatusChangedEvent c = (ConnectionStatusChangedEvent) e;
			setConnectionStatus(c.getContactId());
		}
	}

	@UiThread
	private void setConnectionStatus(ContactId c) {
		if (listener == null) throw new IllegalStateException();
		if (contacts.contains(c)) {
			int online = getOnlineCount();
			listener.onSharingInfoUpdated(contacts.size(), online);
		}
	}

	@Override
	public void addAll(Collection<ContactId> c) {
		contacts.addAll(c);
	}

	@Override
	public void add(ContactId c) {
		contacts.add(c);
	}

	@Override
	public void remove(ContactId c) {
		contacts.remove(c);
	}

	@Override
	public int getOnlineCount() {
		int online = 0;
		for (ContactId c : contacts) {
			if (connectionRegistry.getConnectionStatus(c) == CONNECTED) {
				online++;
			}
		}
		return online;
	}

	@Override
	public int getTotalCount() {
		return contacts.size();
	}

}
