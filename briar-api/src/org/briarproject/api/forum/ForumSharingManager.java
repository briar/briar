package org.briarproject.api.forum;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface ForumSharingManager {

	/** Returns the unique ID of the forum sharing client. */
	ClientId getClientId();

	/**
	 * Sends an invitation to share the given forum with the given contact
	 * and sends an optional message along with it.
	 */
	void sendForumInvitation(GroupId groupId, ContactId contactId,
			String message)	throws DbException;

	/**
	 * Responds to a pending forum invitation
	 */
	void respondToInvitation(Forum f, boolean accept) throws DbException;

	/**
	 * Returns all forum sharing messages sent by the Contact
	 * identified by contactId.
	 */
	Collection<ForumInvitationMessage> getForumInvitationMessages(
			ContactId contactId) throws DbException;

	/** Returns all forums to which the user could subscribe. */
	Collection<Forum> getAvailableForums() throws DbException;

	/** Returns all contacts who are sharing the given forum with us. */
	Collection<Contact> getSharedBy(GroupId g) throws DbException;

	/** Returns the IDs of all contacts with whom the given forum is shared. */
	Collection<ContactId> getSharedWith(GroupId g) throws DbException;

	/** Returns true if the forum not already shared and no invitation is open */
	boolean canBeShared(GroupId g, Contact c) throws DbException;

}
