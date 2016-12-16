package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.ConversationManager.ConversationClient;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface SharingManager<S extends Shareable>
		extends ConversationClient {

	/**
	 * Sends an invitation to share the given group with the given contact
	 * and sends an optional message along with it.
	 */
	void sendInvitation(GroupId shareableId, ContactId contactId,
			@Nullable String message, long timestamp) throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(S s, Contact c, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending group invitation
	 */
	void respondToInvitation(ContactId c, SessionId id, boolean accept)
			throws DbException;

	/**
	 * Returns all group sharing messages sent by the Contact
	 * identified by contactId.
	 */
	Collection<InvitationMessage> getInvitationMessages(
			ContactId contactId) throws DbException;

	/**
	 * Returns all invitations to groups.
	 */
	Collection<SharingInvitationItem> getInvitations() throws DbException;

	/**
	 * Returns all contacts with whom the given group is shared.
	 */
	Collection<Contact> getSharedWith(GroupId g) throws DbException;

	/**
	 * Returns true if the group not already shared and no invitation is open
	 */
	boolean canBeShared(GroupId g, Contact c) throws DbException;

}
