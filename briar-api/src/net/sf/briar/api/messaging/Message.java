package net.sf.briar.api.messaging;

public interface Message {

	/** Returns the message's unique identifier. */
	MessageId getId();

	/**
	 * Returns the identifier of the message's parent, or null if this is the
	 * first message in a thread.
	 */
	MessageId getParent();

	/**
	 * Returns the identifier of the {@link Group} to which the message
	 * belongs, or null if this is a private message.
	 */
	GroupId getGroup();

	/**
	 * Returns the identifier of the message's {@link Author}, or null if this
	 * is an anonymous message.
	 */
	AuthorId getAuthor();

	/** Returns the message's subject line. */
	String getSubject();

	/** Returns the timestamp created by the message's {@link Author}. */
	long getTimestamp();

	/** Returns the serialised message. */
	byte[] getSerialised();

	/** Returns the offset of the message body within the serialised message. */
	int getBodyStart();

	/** Returns the length of the message body in bytes. */
	int getBodyLength();
}