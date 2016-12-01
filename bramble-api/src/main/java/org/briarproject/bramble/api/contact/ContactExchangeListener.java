package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface ContactExchangeListener {

	void contactExchangeSucceeded(Author remoteAuthor);

	/**
	 * The exchange failed because the contact already exists.
	 */
	void duplicateContact(Author remoteAuthor);

	/**
	 * A general failure.
	 */
	void contactExchangeFailed();
}
