package org.briarproject.api.blogs;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sharing.SharingManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface BlogSharingManager
		extends SharingManager<Blog, BlogInvitationMessage> {

	/**
	 * Returns the unique ID of the blog sharing client.
	 */
	ClientId getClientId();

	/**
	 * Sends an invitation to share the given blog with the given contact
	 * and sends an optional message along with it.
	 */
	void sendInvitation(GroupId groupId, ContactId contactId,
			String message) throws DbException;

	/**
	 * Responds to a pending blog invitation
	 */
	void respondToInvitation(Blog b, Contact c, boolean accept)
			throws DbException;

	/**
	 * Returns all blogs sharing messages sent by the Contact
	 * identified by contactId.
	 */
	Collection<BlogInvitationMessage> getInvitationMessages(
			ContactId contactId) throws DbException;

	/**
	 * Returns a specific blog sharing message sent by the Contact
	 * identified by contactId.
	 */
	BlogInvitationMessage getInvitationMessage(ContactId contactId,
			MessageId messageId) throws DbException;

	/**
	 * Returns all blogs to which the user has been invited.
	 */
	Collection<Blog> getInvited() throws DbException;

	/**
	 * Returns all contacts who are sharing the given blog with us.
	 */
	Collection<Contact> getSharedBy(GroupId g) throws DbException;

	/**
	 * Returns the IDs of all contacts with whom the given blog is shared.
	 */
	Collection<Contact> getSharedWith(GroupId g) throws DbException;

	/**
	 * Returns true if the blog not already shared and no invitation is open
	 */
	boolean canBeShared(GroupId g, Contact c) throws DbException;

}
