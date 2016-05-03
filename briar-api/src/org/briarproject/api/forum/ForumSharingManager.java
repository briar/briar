package org.briarproject.api.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface ForumSharingManager {

	/** Returns the unique ID of the forum sharing client. */
	ClientId getClientId();

	/** Returns all forums to which the user could subscribe. */
	Collection<Forum> getAvailableForums() throws DbException;

	/** Returns all contacts who are sharing the given forum with the user. */
	Collection<Contact> getSharedBy(GroupId g) throws DbException;

	/** Returns the IDs of all contacts with whom the given forum is shared. */
	Collection<ContactId> getSharedWith(GroupId g) throws DbException;

	/**
	 * Shares a forum with the given contacts and unshares it with any other
	 * contacts.
	 */
	void setSharedWith(GroupId g, Collection<ContactId> shared)
			throws DbException;

}
