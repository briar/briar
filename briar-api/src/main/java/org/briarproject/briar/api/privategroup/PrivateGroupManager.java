package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;

import java.util.Collection;

@NotNullByDefault
public interface PrivateGroupManager {

	/**
	 * The unique ID of the private group client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.privategroup");

	/**
	 * Adds a new private group and joins it.
	 *
	 * @param group The private group to add
	 * @param joinMsg The new member's join message
	 * @param creator True if the group is added by its creator
	 */
	void addPrivateGroup(PrivateGroup group, GroupMessage joinMsg,
			boolean creator) throws DbException;

	/**
	 * Adds a new private group and joins it.
	 *
	 * @param group The private group to add
	 * @param joinMsg The new member's join message
	 * @param creator True if the group is added by its creator
	 */
	void addPrivateGroup(Transaction txn, PrivateGroup group,
			GroupMessage joinMsg, boolean creator) throws DbException;

	/**
	 * Removes a dissolved private group.
	 */
	void removePrivateGroup(GroupId g) throws DbException;

	/**
	 * Returns the ID of the user's previous message sent to the group
	 */
	MessageId getPreviousMsgId(GroupId g) throws DbException;

	/**
	 * Marks the given private group as dissolved.
	 */
	void markGroupDissolved(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns true if the given private group has been dissolved.
	 */
	boolean isDissolved(GroupId g) throws DbException;

	/**
	 * Stores and sends a local private group message.
	 */
	GroupMessageHeader addLocalMessage(GroupMessage p) throws DbException;

	/**
	 * Returns the private group with the given ID.
	 */
	PrivateGroup getPrivateGroup(GroupId g) throws DbException;

	/**
	 * Returns the private group with the given ID.
	 */
	PrivateGroup getPrivateGroup(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns all private groups the user is a member of.
	 */
	Collection<PrivateGroup> getPrivateGroups() throws DbException;

	/**
	 * Returns the body of the private group message with the given ID.
	 */
	String getMessageBody(MessageId m) throws DbException;

	/**
	 * Returns the headers of all messages in the given private group.
	 */
	Collection<GroupMessageHeader> getHeaders(GroupId g) throws DbException;

	/**
	 * Returns all members of the given private group.
	 */
	Collection<GroupMember> getMembers(GroupId g) throws DbException;

	/**
	 * Returns true if the given author is a member of the given private group.
	 */
	boolean isMember(Transaction txn, GroupId g, Author a) throws DbException;

	/**
	 * Returns the group count for the given private group.
	 */
	GroupCount getGroupCount(GroupId g) throws DbException;

	/**
	 * Marks a message as read or unread and updates the group count.
	 */
	void setReadFlag(GroupId g, MessageId m, boolean read) throws DbException;

	/**
	 * Called when a contact relationship has been revealed between the user
	 * and the given author in the given private group.
	 *
	 * @param byContact True if the contact revealed the relationship first,
	 * otherwise false.
	 */
	void relationshipRevealed(Transaction txn, GroupId g, AuthorId a,
			boolean byContact) throws FormatException, DbException;

	/**
	 * Registers a hook to be called when members are added or private groups
	 * are removed.
	 */
	void registerPrivateGroupHook(PrivateGroupHook hook);

	@NotNullByDefault
	interface PrivateGroupHook {

		void addingMember(Transaction txn, GroupId g, Author a)
				throws DbException;

		void removingGroup(Transaction txn, GroupId g) throws DbException;

	}

}
