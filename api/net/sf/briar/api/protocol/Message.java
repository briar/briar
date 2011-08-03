package net.sf.briar.api.protocol;

public interface Message {

	/** The maximum size of a serialised message, in bytes. */
	static final int MAX_SIZE = (1024 * 1024) - 200;

	/** The maximum size of a signature, in bytes. */
	static final int MAX_SIGNATURE_LENGTH = 100;

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