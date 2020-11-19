package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;

import javax.annotation.Nullable;

@NotNullByDefault
public interface IntroductionManager extends ConversationClient {

	/**
	 * The unique ID of the introduction client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.introduction");

	/**
	 * The current major version of the introduction client.
	 */
	int MAJOR_VERSION = 1;

	/**
	 * Returns true if both contacts can be introduced at this moment.
	 */
	boolean canIntroduce(Contact c1, Contact c2) throws DbException;

	/**
	 * The current minor version of the introduction client.
	 */
	int MINOR_VERSION = 1;

	/**
	 * Sends two initial introduction messages.
	 */
	void makeIntroduction(Contact c1, Contact c2, @Nullable String text,
			long timestamp) throws DbException;

	/**
	 * Responds to an introduction.
	 */
	void respondToIntroduction(ContactId contactId, SessionId sessionId,
			long timestamp, boolean accept) throws DbException;

}
