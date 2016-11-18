package org.briarproject.api.privategroup;

import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import static org.briarproject.api.privategroup.PrivateGroupManager.CLIENT_ID;

@NotNullByDefault
public interface GroupMessageFactory {

	String SIGNING_LABEL_JOIN = CLIENT_ID + "/JOIN";
	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";

	/**
	 * Creates a join announcement message for the creator of a group.
	 *
	 * @param groupId     The ID of the Group that is being joined
	 * @param timestamp   Must be greater than the timestamp of the invitation message
	 * @param creator     The creator's LocalAuthor
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor creator);

	/**
	 * Creates a join announcement message for a joining member.
	 *
	 * @param groupId          The ID of the Group that is being joined
	 * @param timestamp        Must be greater than the timestamp of the
	 *                         invitation message
	 * @param member           The member's LocalAuthor
	 * @param inviteTimestamp  The timestamp of the group invitation message
	 * @param creatorSignature The creator's signature from the group invitation
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, byte[] creatorSignature);

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
