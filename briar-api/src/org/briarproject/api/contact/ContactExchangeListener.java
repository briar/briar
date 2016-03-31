package org.briarproject.api.contact;

import org.briarproject.api.identity.Author;

public interface ContactExchangeListener {

	void contactExchangeSucceeded(Author remoteAuthor);

	/** The exchange failed because the contact already exists. */
	void duplicateContact(Author remoteAuthor);

	/** A general failure. */
	void contactExchangeFailed();
}
