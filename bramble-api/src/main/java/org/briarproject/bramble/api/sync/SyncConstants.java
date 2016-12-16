package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.UniqueId;

public interface SyncConstants {

	/**
	 * The current version of the sync protocol.
	 */
	byte PROTOCOL_VERSION = 0;

	/**
	 * The length of the record header in bytes.
	 */
	int RECORD_HEADER_LENGTH = 4;

	/**
	 * The maximum length of the record payload in bytes.
	 */
	int MAX_RECORD_PAYLOAD_LENGTH = 48 * 1024; // 48 KiB

	/** The maximum length of a group descriptor in bytes. */
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
	int MAX_MESSAGE_IDS = MAX_RECORD_PAYLOAD_LENGTH / UniqueId.LENGTH;
}
