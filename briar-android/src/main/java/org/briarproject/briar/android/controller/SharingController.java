package org.briarproject.briar.android.controller;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.DestroyableContext;

import java.util.Collection;

@NotNullByDefault
public interface SharingController {

	/**
	 * Sets the listener that is called when contacts go on or offline.
	 */
	@UiThread
	void setSharingListener(SharingListener listener);

	/**
	 * Call this when your lifecycle starts,
	 * so the listener will be called when information changes.
	 */
	@UiThread
	void onStart();

	/**
	 * Call this when your lifecycle stops,
	 * so that the controller knows it can stops listening to events.
	 */
	@UiThread
	void onStop();

	/**
	 * Adds one contact to be tracked.
	 */
	@UiThread
	void add(ContactId c);

	/**
	 * Adds a collection of contacts to be tracked.
	 */
	@UiThread
	void addAll(Collection<ContactId> contacts);

	/**
	 * Call this when the contact identified by c is no longer sharing
	 * the given group identified by GroupId g.
	 */
	@UiThread
	void remove(ContactId c);

	/**
	 * Returns the number of online contacts.
	 */
	@UiThread
	int getOnlineCount();

	/**
	 * Returns the total number of contacts that have been added.
	 */
	@UiThread
	int getTotalCount();

	interface SharingListener extends DestroyableContext {
		@UiThread
		void onSharingInfoUpdated(int total, int online);
	}

}
