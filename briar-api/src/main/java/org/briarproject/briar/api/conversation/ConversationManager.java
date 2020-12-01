package org.briarproject.briar.api.conversation;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.Collection;
import java.util.Set;

@NotNullByDefault
public interface ConversationManager {

	int DELETE_SESSION_INTRODUCTION_INCOMPLETE = 1;
	int DELETE_SESSION_INVITATION_INCOMPLETE = 1 << 1;
	int DELETE_SESSION_INTRODUCTION_IN_PROGRESS = 1 << 2;
	int DELETE_SESSION_INVITATION_IN_PROGRESS = 1 << 3;
	int DELETE_NOT_DOWNLOADED = 1 << 4;

	/**
	 * Clients that present messages in a private conversation need to
	 * register themselves here.
	 */
	void registerConversationClient(ConversationClient client);

	/**
	 * Returns the headers of all messages in the given private conversation.
	 * <p>
	 * Only {@link MessagingManager} returns only headers.
	 * The others also return the message text.
	 */
	Collection<ConversationMessageHeader> getMessageHeaders(ContactId c)
			throws DbException;

	/**
	 * Returns the unified group count for all private conversation messages.
	 */
	GroupCount getGroupCount(ContactId c) throws DbException;

	/**
	 * Returns the unified group count for all private conversation messages.
	 */
	GroupCount getGroupCount(Transaction txn, ContactId c) throws DbException;

	/**
	 * Returns a timestamp for an outgoing message, which is later than the
	 * timestamp of any visible message sent or received so far.
	 */
	long getTimestampForOutgoingMessage(Transaction txn, ContactId c)
			throws DbException;

	/**
	 * Deletes all messages exchanged with the given contact.
	 */
	DeletionResult deleteAllMessages(ContactId c) throws DbException;

	/**
	 * Deletes the given set of messages associated with the given contact.
	 */
	DeletionResult deleteMessages(ContactId c, Collection<MessageId> messageIds)
			throws DbException;

	@NotNullByDefault
	interface ConversationClient {

		Group getContactGroup(Contact c);

		Collection<ConversationMessageHeader> getMessageHeaders(Transaction txn,
				ContactId contactId) throws DbException;

		/**
		 * Returns all conversation {@link MessageId}s for the given contact
		 * this client is responsible for.
		 */
		Set<MessageId> getMessageIds(Transaction txn, ContactId contactId)
				throws DbException;

		GroupCount getGroupCount(Transaction txn, ContactId c)
				throws DbException;

		void setReadFlag(GroupId g, MessageId m, boolean read)
				throws DbException;

		/**
		 * Deletes all messages associated with the given contact.
		 */
		DeletionResult deleteAllMessages(Transaction txn,
				ContactId c) throws DbException;

		/**
		 * Deletes the given set of messages associated with the given contact.
		 * <p>
		 * The set of message IDs must only include message IDs returned by
		 * {@link #getMessageIds}.
		 */
		DeletionResult deleteMessages(Transaction txn, ContactId c,
				Set<MessageId> messageIds) throws DbException;
	}

}
