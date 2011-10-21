package net.sf.briar.api.protocol;

public interface Message extends MessageHeader {

	/**
	 * The maximum length of a message body in bytes. To allow for future
	 * changes in the protocol, this is smaller than the maximum packet length
	 * even when all the message's other fields have their maximum lengths.
	 */
	static final int MAX_BODY_LENGTH =
		ProtocolConstants.MAX_PACKET_LENGTH - 1024;

	/** The maximum length of a subject line in UTF-8 bytes. */
	static final int MAX_SUBJECT_LENGTH = 100;

	/** The maximum length of a signature in bytes. */
	static final int MAX_SIGNATURE_LENGTH = 100;

	/** The length of the random salt in bytes. */
	static final int SALT_LENGTH = 8;

	/** Returns the length of the serialised message in bytes. */
	int getLength();

	/** Returns the serialised message. */
	byte[] getSerialised();

	/** Returns the offset of the message body within the serialised message. */
	int getBodyStart();

	/** Returns the length of the message body in bytes. */
	int getBodyLength();
}