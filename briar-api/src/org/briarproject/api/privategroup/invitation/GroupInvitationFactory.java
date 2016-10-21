package org.briarproject.api.privategroup.invitation;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;

public interface GroupInvitationFactory {

	/**
	 * Returns a signature to include when inviting a member to join a private
	 * group. If the member accepts the invitation, the signature will be
	 * included in the member's join message.
	 */
	@CryptoExecutor
	byte[] signInvitation(Contact c, GroupId privateGroupId, long timestamp,
			byte[] privateKey);

	/**
	 * Returns a token to be signed by the creator when inviting a member to
	 * join a private group. If the member accepts the invitation, the
	 * signature will be included in the member's join message.
	 */
	BdfList createInviteToken(AuthorId creatorId, AuthorId memberId,
			GroupId privateGroupId, long timestamp);
}
