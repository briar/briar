package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface GroupInvitationManager {

	/**
	 * The unique ID of the private group invitation client.
	 */
	ClientId CLIENT_ID =
			new ClientId("org.briarproject.briar.privategroup.invitation");

	/**
	 * Sends an invitation to share the given private group with the given
	 * contact, including an optional message.
	 */
	void sendInvitation(GroupId g, ContactId c, @Nullable String message,
			long timestamp, byte[] signature) throws DbException;

	/**
	 * Responds to a pending private group invitation from the given contact.
	 */
	void respondToInvitation(ContactId c, PrivateGroup g, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending private group invitation from the given contact.
	 */
	void respondToInvitation(ContactId c, SessionId s, boolean accept)
			throws DbException;

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
