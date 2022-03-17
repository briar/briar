package org.briarproject.bramble.api.mailbox;

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
}
