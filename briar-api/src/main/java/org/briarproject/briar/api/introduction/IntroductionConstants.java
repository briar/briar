package org.briarproject.briar.api.introduction;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface IntroductionConstants {

	/**
	 * The maximum length of the introducer's optional message to the
	 * introducees in UTF-8 bytes.
	 */
	int MAX_INTRODUCTION_TEXT_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	String LABEL_SESSION_ID = "org.briarproject.briar.introduction/SESSION_ID";

	String LABEL_MASTER_KEY = "org.briarproject.briar.introduction/MASTER_KEY";

	String LABEL_ALICE_MAC_KEY =
			"org.briarproject.briar.introduction/ALICE_MAC_KEY";

	String LABEL_BOB_MAC_KEY =
			"org.briarproject.briar.introduction/BOB_MAC_KEY";

	String LABEL_AUTH_MAC = "org.briarproject.briar.introduction/AUTH_MAC";

	String LABEL_AUTH_SIGN = "org.briarproject.briar.introduction/AUTH_SIGN";

	String LABEL_AUTH_NONCE = "org.briarproject.briar.introduction/AUTH_NONCE";

	String LABEL_ACTIVATE_MAC =
			"org.briarproject.briar.introduction/ACTIVATE_MAC";

}
