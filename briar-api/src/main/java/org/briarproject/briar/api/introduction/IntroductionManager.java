package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.ConversationManager.ConversationClient;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface IntroductionManager extends ConversationClient {

	/**
	 * The unique ID of the introduction client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.introduction");

	/**
	 * The current version of the introduction client.
	 */
	int CLIENT_VERSION = 0;

	/**
	 * Sends two initial introduction messages.
	 */
	void makeIntroduction(Contact c1, Contact c2, @Nullable String msg,
			long timestamp) throws DbException, FormatException;

	/**
	 * Accepts an introduction.
	 */
	void acceptIntroduction(ContactId contactId, SessionId sessionId,
			long timestamp) throws DbException, FormatException;

	/**
	 * Declines an introduction.
	 */
	void declineIntroduction(ContactId contactId, SessionId sessionId,
			long timestamp) throws DbException, FormatException;

	/**
	 * Returns all introduction messages for the given contact.
	 */
	Collection<IntroductionMessage> getIntroductionMessages(ContactId contactId)
			throws DbException;

}
