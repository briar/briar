package org.briarproject.api.privategroup;

import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface PrivateGroupManager extends MessageTracker {

	/** Returns the unique ID of the private group client. */
	ClientId getClientId();

	/**
	 * Adds a new private group and joins it.
	 *
	 * @param group        The private group to add
	 * @param newMemberMsg The creator's message announcing herself as
	 *                     first new member
	 * @param joinMsg      The creator's own join message
	 */
	void addPrivateGroup(PrivateGroup group, GroupMessage newMemberMsg,
			GroupMessage joinMsg) throws DbException;

	/** Removes a dissolved private group. */
	void removePrivateGroup(GroupId g) throws DbException;

	/** Gets the MessageId of your previous message sent to the group */
	MessageId getPreviousMsgId(GroupId g) throws DbException;

	/** Returns the timestamp of the message with the given ID */
	// TODO change to getPreviousMessageHeader()
	long getMessageTimestamp(MessageId id) throws DbException;

	/** Stores (and sends) a local group message. */
	GroupMessageHeader addLocalMessage(GroupMessage p) throws DbException;

	/** Returns the private group with the given ID. */
	PrivateGroup getPrivateGroup(GroupId g) throws DbException;

	/**
	 * Returns the private group with the given ID within the given transaction.
	 */
	PrivateGroup getPrivateGroup(Transaction txn, GroupId g) throws DbException;

	/** Returns all private groups the user is a member of. */
	Collection<PrivateGroup> getPrivateGroups() throws DbException;

	/** Returns true if the private group has been dissolved. */
	boolean isDissolved(GroupId g) throws DbException;

	/** Returns the body of the group message with the given ID. */
	String getMessageBody(MessageId m) throws DbException;

	/** Returns the headers of all group messages in the given group. */
	Collection<GroupMessageHeader> getHeaders(GroupId g) throws DbException;

}
