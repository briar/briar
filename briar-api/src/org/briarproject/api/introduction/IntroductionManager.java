package org.briarproject.api.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;

import java.util.Collection;

public interface IntroductionManager extends MessageTracker {

	/** The unique ID of the introduction client. */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.introduction");

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
