package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface SharingManager<S extends Shareable>
		extends ConversationClient {

	enum SharingStatus {
		/**
		 * The {@link Shareable} can be shared with the requested contact.
		 */
		SHAREABLE,
		/**
		 * The {@link Shareable} can not be shared with the requested contact,
		 * because the contact was already invited.
		 */
		INVITE_SENT,
		/**
		 * The {@link Shareable} can not be shared with the requested contact,
		 * because the contact has already invited us.
		 */
		INVITE_RECEIVED,
		/**
		 * The {@link Shareable} can not be shared with the requested contact,
		 * because it is already being shared.
		 */
		SHARING,
		/**
		 * The {@link Shareable} can not be shared with the requested contact,
		 * because it is not supported by that contact.
		 * This could be a missing or outdated client.
		 */
		NOT_SUPPORTED,
		/**
		 * The sharing session has encountered an error.
		 */
		ERROR
	}

	/**
	 * Sends an invitation to share the given group with the given contact,
	 * including optional text.
	 */
	void sendInvitation(GroupId shareableId, ContactId contactId,
			@Nullable String text) throws DbException;

	/**
	 * Sends an invitation to share the given group with the given contact,
	 * including optional text.
	 */
	void sendInvitation(Transaction txn, GroupId shareableId,
			ContactId contactId, @Nullable String text) throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(S s, Contact c, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(Transaction txn, S s, Contact c, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(ContactId c, SessionId id, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(Transaction txn, ContactId c, SessionId id,
			boolean accept) throws DbException;

	/**
	 * Returns all invitations to groups.
	 */
	Collection<SharingInvitationItem> getInvitations() throws DbException;

	/**
	 * Returns all invitations to groups.
	 */
	Collection<SharingInvitationItem> getInvitations(Transaction txn)
			throws DbException;

	/**
	 * Returns all contacts with whom the given group is shared.
	 */
	Collection<Contact> getSharedWith(GroupId g) throws DbException;

	/**
	 * Returns all contacts with whom the given group is shared.
	 */
	Collection<Contact> getSharedWith(Transaction txn, GroupId g)
			throws DbException;

	/**
	 * Returns the current {@link SharingStatus} for the given {@link Contact}
	 * and {@link Shareable} identified by the given {@link GroupId}.
	 * This indicates whether the {@link Shareable} can be shared
	 * with the contact.
	 *
	 * @throws ProtocolStateException if we already left the {@link Shareable}.
	 */
	SharingStatus getSharingStatus(GroupId g, Contact c) throws DbException;

	/**
	 * Returns the current {@link SharingStatus} for the given {@link Contact}
	 * and {@link Shareable} identified by the given {@link GroupId}.
	 * This indicates whether the {@link Shareable} can be shared
	 * with the contact.
	 *
	 * @throws ProtocolStateException if we already left the {@link Shareable}.
	 */
	SharingStatus getSharingStatus(Transaction txn, GroupId g, Contact c)
			throws DbException;

}
