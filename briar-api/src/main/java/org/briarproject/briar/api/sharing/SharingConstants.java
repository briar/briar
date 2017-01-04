package org.briarproject.briar.api.sharing;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface SharingConstants {

	/**
	 * The maximum length of the optional message from the inviter to the
	 * invitee in UTF-8 bytes.
	 */
	int MAX_INVITATION_MESSAGE_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

}
