package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConstants;
import org.briarproject.bramble.api.plugin.TransportId;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;

public interface MailboxConstants {

	/**
	 * The transport ID of the mailbox plugin.
	 */
	TransportId ID = new TransportId("org.briarproject.bramble.mailbox");

	/**
	 * The QR code format identifier, used to distinguish mailbox QR codes
	 * from QR codes used for other purposes. See
	 * {@link KeyAgreementConstants#QR_FORMAT_ID};
	 */
	byte QR_FORMAT_ID = 1;

	/**
	 * The QR code format version.
	 */
	byte QR_FORMAT_VERSION = 0;

	/**
	 * Mailbox API versions that we support as a client. This is reported to our
	 * contacts by {@link MailboxUpdateManager}.
	 */
	List<MailboxVersion> CLIENT_SUPPORTS = singletonList(
			new MailboxVersion(1, 0));

	/**
	 * The constant returned by
	 * {@link MailboxHelper#getHighestCommonMajorVersion(List, List)}
	 * when the server is too old to support our major version.
	 */
	int API_SERVER_TOO_OLD = -1;

	/**
	 * The constant returned by
	 * {@link MailboxHelper#getHighestCommonMajorVersion(List, List)}
	 * when we as a client are too old to support the server's major version.
	 */
	int API_CLIENT_TOO_OLD = -2;

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

	/**
	 * The maximum latency of the mailbox transport in milliseconds.
	 */
	long MAX_LATENCY = DAYS.toMillis(14);
}
