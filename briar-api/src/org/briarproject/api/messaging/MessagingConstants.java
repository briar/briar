package org.briarproject.api.messaging;

import static org.briarproject.api.transport.TransportConstants.MIN_STREAM_LENGTH;

public interface MessagingConstants {

	/**
	 * The maximum length of a serialised packet in bytes. To allow for future
	 * changes in the protocol, this is smaller than the minimum stream length
	 * minus the maximum encryption and authentication overhead.
	 */
	int MAX_PACKET_LENGTH = MIN_STREAM_LENGTH / 2;

	/** The maximum number of public groups a user may subscribe to. */
	int MAX_SUBSCRIPTIONS = 3000;

	/** The maximum length of a group's name in UTF-8 bytes. */
	int MAX_GROUP_NAME_LENGTH = 50;

	/** The length of a group's random salt in bytes. */
	int GROUP_SALT_LENGTH = 32;

	/**
	 * The maximum length of a message body in bytes. To allow for future
	 * changes in the protocol, this is smaller than the maximum packet length
	 * even when all the message's other fields have their maximum lengths.
	 */
	int MAX_BODY_LENGTH = MAX_PACKET_LENGTH - 1024;

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
