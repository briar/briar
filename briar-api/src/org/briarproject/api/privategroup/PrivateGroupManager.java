package org.briarproject.api.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

@NotNullByDefault
public interface PrivateGroupManager extends MessageTracker {

	/**
	 * The unique ID of the private group client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.privategroup");

	/**
	 * Adds a new private group and joins it.
	 *
	 * @param group        The private group to add
	 * @param joinMsg      The creators's join message
	 * @param creator      True if the group is added by its creator
	 */
	void addPrivateGroup(PrivateGroup group, GroupMessage joinMsg,
			boolean creator) throws DbException;

	/**
	 * Adds a new private group and joins it.
	 *
	 * @param group        The private group to add
	 * @param joinMsg      The new member's join message
	 * @param creator      True if the group is added by its creator
	 */
	void addPrivateGroup(Transaction txn, PrivateGroup group,
			GroupMessage joinMsg, boolean creator) throws DbException;

	/**
	 * Removes a dissolved private group.
	 */
	void removePrivateGroup(GroupId g) throws DbException;

	/**
	 * Gets the MessageId of the user's previous message sent to the group
	 */
	MessageId getPreviousMsgId(GroupId g) throws DbException;

	/**
	 * Marks the group with GroupId g as resolved
	 */
	void markGroupDissolved(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns true if the private group has been dissolved.
	 */
	boolean isDissolved(GroupId g) throws DbException;

	/**
	 * Stores (and sends) a local group message.
	 */
	GroupMessageHeader addLocalMessage(GroupMessage p) throws DbException;

	/**
	 * Returns the private group with the given ID.
	 */
	PrivateGroup getPrivateGroup(GroupId g) throws DbException;

	/**
	 * Returns the private group with the given ID within the given transaction.
	 */
	PrivateGroup getPrivateGroup(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns all private groups the user is a member of.
	 */
	Collection<PrivateGroup> getPrivateGroups() throws DbException;

	/**
	 * Returns the body of the group message with the given ID.
	 */
	String getMessageBody(MessageId m) throws DbException;

	/**
	 * Returns the headers of all group messages in the given group.
	 */
	Collection<GroupMessageHeader> getHeaders(GroupId g) throws DbException;

	/**
	 * Returns all members of the group with ID g
	 */
	Collection<GroupMember> getMembers(GroupId g) throws DbException;

	/**
	 * Returns true if the given Author a is member of the group with ID g
	 */
	boolean isMember(Transaction txn, GroupId g, Author a) throws DbException;

	/**
	 * This method needs to be called when a contact relationship
	 * has been revealed between the user and the Author with AuthorId a
	 * in the Group identified by the GroupId g.
	 *
	 * @param byContact true if the remote contact has revealed
	 *                     the relationship first. Otherwise false.
	 */
	void relationshipRevealed(Transaction txn, GroupId g, AuthorId a,
			boolean byContact) throws FormatException, DbException;

	/**
	 * Registers a hook to be called when members are added
	 * or groups are removed.
	 */
	void registerPrivateGroupHook(PrivateGroupHook hook);

	@NotNullByDefault
	interface PrivateGroupHook {

		void addingMember(Transaction txn, GroupId g, Author a)
				throws DbException;

		void removingGroup(Transaction txn, GroupId g) throws DbException;

	}

}
