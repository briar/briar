package org.briarproject.briar.api.privategroup.invitation;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.ConversationManager.ConversationClient;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.sharing.InvitationMessage;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface GroupInvitationManager extends ConversationClient {

	/**
	 * The unique ID of the private group invitation client.
	 */
	ClientId CLIENT_ID =
			new ClientId("org.briarproject.briar.privategroup.invitation");

	/**
	 * Sends an invitation to share the given private group with the given
	 * contact, including an optional message.
	 *
	 * @throws ProtocolStateException if the group is no longer eligible to be
	 * shared with the contact, for example because an invitation is already
	 * pending.
	 */
	void sendInvitation(GroupId g, ContactId c, @Nullable String message,
			long timestamp, byte[] signature) throws DbException;

	/**
	 * Responds to a pending private group invitation from the given contact.
	 *
	 * @throws ProtocolStateException if the invitation is no longer pending,
	 * for example because the group has been dissolved.
	 */
	void respondToInvitation(ContactId c, PrivateGroup g, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending private group invitation from the given contact.
	 *
	 * @throws ProtocolStateException if the invitation is no longer pending,
	 * for example because the group has been dissolved.
	 */
	void respondToInvitation(ContactId c, SessionId s, boolean accept)
			throws DbException;

	/**
	 * Makes the user's relationship with the given contact visible to the
	 * given private group.
	 *
	 * @throws ProtocolStateException if the relationship is no longer eligible
	 * to be revealed, for example because the contact has revealed it.
	 */
	void revealRelationship(ContactId c, GroupId g) throws DbException;

	/**
	 * Returns all private group invitation messages related to the given
	 * contact.
	 */
	Collection<InvitationMessage> getInvitationMessages(ContactId c)
			throws DbException;

	/**
	 * Returns all private groups to which the user has been invited.
	 */
	Collection<GroupInvitationItem> getInvitations() throws DbException;

	/**
	 * Returns true if the given contact can be invited to the given private
	 * group.
	 */
	boolean isInvitationAllowed(Contact c, GroupId g) throws DbException;
}
