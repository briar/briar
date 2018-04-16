package org.briarproject.briar.api.introduction2;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface IntroductionConstants {

	/**
	 * The maximum length of the introducer's optional message to the
	 * introducees in UTF-8 bytes.
	 */
	int MAX_REQUEST_MESSAGE_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

}
