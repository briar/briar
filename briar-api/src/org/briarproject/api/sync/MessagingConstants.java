package org.briarproject.api.sync;


public interface MessagingConstants {

	/** The current version of the messaging protocol. */
	byte PROTOCOL_VERSION = 0;

	/** The length of the packet header in bytes. */
	int HEADER_LENGTH = 4;

	/** The maximum length of the packet payload in bytes. */
	int MAX_PAYLOAD_LENGTH = 32 * 1024; // 32 KiB

	/** The maximum number of public groups a user may subscribe to. */
	int MAX_SUBSCRIPTIONS = 300;

	/** The maximum length of a group's name in UTF-8 bytes. */
	int MAX_GROUP_NAME_LENGTH = 50;

	/** The length of a group's random salt in bytes. */
	int GROUP_SALT_LENGTH = 32;

	/**
	 * The maximum length of a message body in bytes. To allow for future
	 * changes in the protocol, this is smaller than the maximum payload length
	 * even when all the message's other fields have their maximum lengths.
	 */
	int MAX_BODY_LENGTH = MAX_PAYLOAD_LENGTH - 1024;

	/** The maximum length of a message's content type in UTF-8 bytes. */
	int MAX_CONTENT_TYPE_LENGTH = 50;

	/** The maximum length of a message's subject line in UTF-8 bytes. */
	int MAX_SUBJECT_LENGTH = 100;

	/** The length of a message's random salt in bytes. */
	int MESSAGE_SALT_LENGTH = 32;

	/**
	 * When calculating the retention time of the database, the timestamp of
	 * the oldest message in the database is rounded down to a multiple of
	 * this value to avoid revealing the presence of any particular message.
	 */
	int RETENTION_GRANULARITY = 60 * 1000; // 1 minute
}
