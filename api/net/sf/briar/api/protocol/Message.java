package net.sf.briar.api.protocol;

public interface Message {

	/**
	 * The maximum length of a message body in bytes. To allow for future
	 * changes in the protocol, this is smaller than the maximum packet length
	 * even when all the message's other fields have their maximum lengths.
	 */
	static final int MAX_BODY_LENGTH =
		ProtocolConstants.MAX_PACKET_LENGTH - 1024;

	/** The maximum length of a signature in bytes. */
	static final int MAX_SIGNATURE_LENGTH = 100;

	/** The length of the random salt in bytes. */
	static final int SALT_LENGTH = 8;

	/** Returns the message's unique identifier. */
	MessageId getId();

	/**
	 * Returns the message's parent, or MessageId.NONE if this is the first
	 * message in a thread.
	 */
	MessageId getParent();

	/** Returns the group to which the message belongs. */
	GroupId getGroup();

	/** Returns the message's author. */
	AuthorId getAuthor();

	/** Returns the timestamp created by the message's author. */
	long getTimestamp();

	/** Returns the size of the message in bytes. */
	int getSize();

	/** Returns the serialised representation of the message. */
	byte[] getBytes();
}