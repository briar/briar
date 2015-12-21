package org.briarproject.api.sync;

import org.briarproject.api.UniqueId;

public interface SyncConstants {

	/** The current version of the sync protocol. */
	byte PROTOCOL_VERSION = 0;

	/** The length of the packet header in bytes. */
	int PACKET_HEADER_LENGTH = 4;

	/** The maximum length of the packet payload in bytes. */
	int MAX_PACKET_PAYLOAD_LENGTH = 32 * 1024; // 32 KiB

	/** The maximum number of groups a user may subscribe to. */
	int MAX_SUBSCRIPTIONS = 200;

	/** The maximum length of a group descriptor in bytes. */
	int MAX_GROUP_DESCRIPTOR_LENGTH = 100;

	/** The maximum length of a message in bytes. */
	int MAX_MESSAGE_LENGTH = MAX_PACKET_PAYLOAD_LENGTH - PACKET_HEADER_LENGTH;

	/** The length of the message header in bytes. */
	int MESSAGE_HEADER_LENGTH = UniqueId.LENGTH + 8;

	/** The maximum length of a message body in bytes. */
	int MAX_MESSAGE_BODY_LENGTH = MAX_MESSAGE_LENGTH - MESSAGE_HEADER_LENGTH;
}
