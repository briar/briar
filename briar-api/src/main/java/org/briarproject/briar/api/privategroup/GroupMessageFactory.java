package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.privategroup.PrivateGroupManager.CLIENT_ID;

@NotNullByDefault
public interface GroupMessageFactory {

	String SIGNING_LABEL_JOIN = CLIENT_ID + "/JOIN";
	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";

	/**
	 * Creates a join announcement message for the creator of a group.
	 *
	 * @param groupId The ID of the private group that is being joined
	 * @param timestamp The timestamp to be used in the join announcement
	 * @param creator The creator's identity
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor creator);

	/**
	 * Creates a join announcement message for a joining member.
	 *
	 * @param groupId The ID of the private group that is being joined
	 * @param timestamp The timestamp to be used in the join announcement,
	 * which must be greater than the timestamp of the invitation message
	 * @param member The member's identity
	 * @param inviteTimestamp The timestamp of the invitation message
	 * @param creatorSignature The creator's signature from the invitation
	 * message
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, byte[] creatorSignature);

	/**
	 * Creates a private group post.
	 *
	 * @param groupId The ID of the private group
	 * @param timestamp Must be greater than the timestamps of the parent
	 * post, if any, and the member's previous message
	 * @param parentId The ID of the parent post, or null if the post has no
	 * parent
	 * @param author The author of the post
	 * @param body The content of the post
	 * @param previousMsgId The ID of the author's previous message
	 * in this group
	 */
	@CryptoExecutor
	GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author, String body,
			MessageId previousMsgId);

}
