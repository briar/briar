package org.briarproject.api.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface IntroductionManager {

	/** Returns the unique ID of the introduction client. */
	ClientId getClientId();

	/**
	 * sends two initial introduction messages
	 */
	void makeIntroduction(Contact c1, Contact c2, String msg)
			throws DbException, FormatException;

	/**
	 * Accept an introduction that had been made
	 */
	void acceptIntroduction(final ContactId contactId,
			final SessionId sessionId) throws DbException, FormatException;

	/**
	 * Decline an introduction that had been made
	 */
	void declineIntroduction(final ContactId contactId,
			final SessionId sessionId) throws DbException, FormatException;

	/**
	 * Get all introduction messages for the contact with this contactId
	 */
	Collection<IntroductionMessage> getIntroductionMessages(ContactId contactId)
			throws DbException;

	/** Marks an introduction message as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;


	/** Get the session state for the given session ID */
	BdfDictionary getSessionState(Transaction txn, GroupId groupId,
			byte[] sessionId) throws DbException, FormatException;

	/** Gets the group used for introductions with Contact c */
	Group getIntroductionGroup(Contact c);

	/** Get the local group used to store session states */
	Group getLocalGroup();

	/** Send an introduction message */
	void sendMessage(Transaction txn, BdfDictionary message)
			throws DbException, FormatException;

}
