package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface GroupInvitationManager extends MessageTracker {

	/** The unique ID of the private group invitation client. */
	ClientId CLIENT_ID =
			new ClientId("org.briarproject.briar.privategroup.invitation");

	/**
	 * Sends an invitation to share the given forum with the given contact
	 * and sends an optional message along with it.
	 */
	void sendInvitation(GroupId groupId, ContactId contactId,
			String message)	throws DbException;

	/**
	 * Responds to a pending private group invitation
	 */
	void respondToInvitation(PrivateGroup g, Contact c, boolean accept)
			throws DbException;

	/**
	 * Responds to a pending private group invitation
	 */
	void respondToInvitation(SessionId id, boolean accept) throws DbException;

	/**
	 * Returns all private group invitation messages related to the contact
	 * identified by contactId.
	 */
	Collection<InvitationMessage> getInvitationMessages(
			ContactId contactId) throws DbException;

	/** Returns all private groups to which the user has been invited. */
	Collection<GroupInvitationItem> getInvitations() throws DbException;

}
