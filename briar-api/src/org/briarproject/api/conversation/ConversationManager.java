package org.briarproject.api.conversation;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.List;

public interface ConversationManager {

	/**
	 * Returns the unique ID of the conversation client.
	 */
	ClientId getClientId();

	/**
	 * Stores a local private message, and returns the corresponding item.
	 */
	ConversationItem addLocalMessage(PrivateMessage m, byte[] body) throws DbException;

	/**
	 * Returns the ID of the contact with the given private conversation.
	 */
	ContactId getContactId(GroupId g) throws DbException;

	/**
	 * Returns the ID of the private conversation with the given contact.
	 */
	GroupId getConversationId(ContactId c) throws DbException;

	/**
	 * Returns all messages in the given private conversation.
	 */
	List<ConversationItem> getMessages(ContactId c) throws DbException;

	/**
	 * Returns all messages in the given private conversation.
	 */
	List<ConversationItem> getMessages(ContactId c, boolean content)
			throws DbException;

	/**
	 * Starts a background task to load the content of the given message.
	 */
	void loadMessageContent(ConversationItem.Partial m);

	/**
	 * Respond to a conversation item with accept/decline.
	 */
	void respondToItem(ContactId c, ConversationItem item, boolean accept,
			long timestamp) throws DbException, FormatException;

	/**
	 * Marks a conversation item as read or unread.
	 */
	void setReadFlag(ConversationItem item, boolean read) throws DbException;
}
