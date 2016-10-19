package org.briarproject.api.privategroup;

import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

public interface GroupMessageFactory {

	/**
	 * Creates a new member announcement that contains the joiner's identity
	 * and is signed by the creator.
	 * <p>
	 * When a new member accepts an invitation to the group,
	 * the creator sends this new member announcement to the group.
	 *
	 * @param groupId   The ID of the group the new member joined
	 * @param timestamp The current timestamp
	 * @param creator   The creator of the group with {@param groupId}
	 * @param member    The new member that has just accepted an invitation
	 */
	@CryptoExecutor
	GroupMessage createNewMemberMessage(GroupId groupId, long timestamp,
			LocalAuthor creator, Author member);

	/**
	 * Creates a join announcement message
	 * that depends on a previous new member announcement.
	 *
	 * @param groupId     The ID of the Group that is being joined
	 * @param timestamp   Must be equal to the timestamp of the new member message
	 * @param member      Our own LocalAuthor
	 * @param newMemberId The MessageId of the new member message
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, MessageId newMemberId);

	/**
	 * Creates a group message
	 *
	 * @param groupId       The ID of the Group that is posted in
	 * @param timestamp     Must be greater than the timestamps of the parentId
	 *                      post, if any, and the member's previous message
	 * @param parentId      The ID of the message that is replied to
	 * @param author        The author of the group message
	 * @param body          The content of the group message
	 * @param previousMsgId The ID of the author's previous message
	 *                      in this group
	 */
	@CryptoExecutor
	GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author, String body,
			MessageId previousMsgId);

}
