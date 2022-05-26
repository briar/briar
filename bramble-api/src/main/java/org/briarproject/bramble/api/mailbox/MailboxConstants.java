package org.briarproject.bramble.api.mailbox;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;

public interface MailboxConstants {

	/**
	 * The maximum length of a file that can be uploaded to or downloaded from
	 * a mailbox.
	 */
	int MAX_FILE_BYTES = 1024 * 1024;

	/**
	 * The maximum length of the plaintext payload of a file, such that the
	 * ciphertext is no more than {@link #MAX_FILE_BYTES}.
	 */
	int MAX_FILE_PAYLOAD_BYTES =
			(MAX_FILE_BYTES - TAG_LENGTH - STREAM_HEADER_LENGTH)
					/ MAX_FRAME_LENGTH * MAX_PAYLOAD_LENGTH;

	/**
	 * The number of connection failures
	 * that indicate a problem with the mailbox.
	 */
	int PROBLEM_NUM_CONNECTION_FAILURES = 5;

	/**
	 * The time in milliseconds since the last connection success
	 * that need to pass to indicates a problem with the mailbox.
	 */
	long PROBLEM_MS_SINCE_LAST_SUCCESS = HOURS.toMillis(1);

}
