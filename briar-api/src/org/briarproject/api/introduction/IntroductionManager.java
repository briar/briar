package org.briarproject.api.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ReadableMessageManager;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface IntroductionManager extends ReadableMessageManager {

	/** Returns the unique ID of the introduction client. */
	ClientId getClientId();

	/**
	 * sends two initial introduction messages
	 */
	void makeIntroduction(Contact c1, Contact c2, String msg,
			final long timestamp)
			throws DbException, FormatException;

	/**
	 * Accept an introduction that had been made
	 */
	void acceptIntroduction(final ContactId contactId,
			final SessionId sessionId, final long timestamp)
			throws DbException, FormatException;

	/**
	 * Decline an introduction that had been made
	 */
	void declineIntroduction(final ContactId contactId,
			final SessionId sessionId, final long timestamp)
			throws DbException, FormatException;

	/**
	 * Get all introduction messages for the contact with this contactId
	 */
	Collection<IntroductionMessage> getIntroductionMessages(ContactId contactId)
			throws DbException;

}
