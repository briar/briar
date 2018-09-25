package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;

import java.nio.ByteBuffer;

import java.util.Collection;

@NotNullByDefault
public interface MessagingManager extends ConversationClient {

	// TODO remove (only for prototype)
	void addNewPendingContact(String name, long timestamp) throws DbException;
	void removePendingContact(String name, long timestamp) throws DbException;
	Collection<PendingContact> getPendingContacts() throws DbException;
	class PendingContact {
		private final String name;
		private final long timestamp;
		public PendingContact(String name, long timestamp) {
			this.name = name;
			this.timestamp = timestamp;
		}
		public String getName() {
			return name;
		}
		public long getTimestamp() {
			return timestamp;
		}
	}


	/**
	 * The unique ID of the messaging client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.messaging");

	/**
	 * The current major version of the messaging client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the messaging client.
	 */
	int MINOR_VERSION = 0;

	/**
	 * Stores a local private message.
	 */
	void addLocalMessage(PrivateMessage m) throws DbException;

	/**
	 * Stores a local attachment message.
	 */
	AttachmentHeader addLocalAttachment(GroupId groupId, long timestamp,
			String contentType, ByteBuffer data) throws DbException;

	/**
	 * Returns the ID of the contact with the given private conversation.
	 */
	ContactId getContactId(GroupId g) throws DbException;

	/**
	 * Returns the ID of the private conversation with the given contact.
	 */
	GroupId getConversationId(ContactId c) throws DbException;

	/**
	 * Returns the text of the private message with the given ID.
	 */
	String getMessageText(MessageId m) throws DbException;

	/**
	 * Returns the attachment with the given ID.
	 */
	Attachment getAttachment(MessageId m) throws DbException;

}
