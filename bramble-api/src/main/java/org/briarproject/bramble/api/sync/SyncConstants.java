package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.UniqueId;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;

public interface SyncConstants {

	/**
	 * The current version of the sync protocol.
	 */
	byte PROTOCOL_VERSION = 0;

	/**
	 * The versions of the sync protocol this peer supports.
	 */
	List<Byte> SUPPORTED_VERSIONS = singletonList(PROTOCOL_VERSION);

	/**
	 * The maximum length of a group descriptor in bytes.
	 */
	int MAX_GROUP_DESCRIPTOR_LENGTH = 16 * 1024; // 16 KiB

	/**
	 * The length of the message header in bytes.
	 */
	int MESSAGE_HEADER_LENGTH = UniqueId.LENGTH + 8;

	/**
	 * The maximum length of a message body in bytes.
	 */
	int MAX_MESSAGE_BODY_LENGTH = 32 * 1024; // 32 KiB

	/**
	 * The maximum length of a message in bytes.
	 */
	int MAX_MESSAGE_LENGTH = MESSAGE_HEADER_LENGTH + MAX_MESSAGE_BODY_LENGTH;

	/**
	 * The maximum number of message IDs in an ack, offer or request record.
	 */
	int MAX_MESSAGE_IDS = MAX_RECORD_PAYLOAD_BYTES / UniqueId.LENGTH;

	/**
	 * The maximum number of versions of the sync protocol a peer may support
	 * simultaneously.
	 */
	int MAX_SUPPORTED_VERSIONS = 10;

	/**
	 * The length of the priority nonce used for choosing between redundant
	 * connections.
	 */
	int PRIORITY_NONCE_BYTES = 16;

	/**
	 * The maximum allowed latency for any transport, in milliseconds.
	 */
	long MAX_TRANSPORT_LATENCY = DAYS.toMillis(365);
}
