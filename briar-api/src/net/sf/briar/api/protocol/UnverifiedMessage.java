package net.sf.briar.api.protocol;

/** A {@link Message} that has not yet had its signatures verified. */
public interface UnverifiedMessage {

	/**
	 * Returns the identifier of the message's parent, or null if this is the
	 * first message in a thread.
	 */
	MessageId getParent();

	/**
	 * Returns the {@link Group} to which the message belongs, or null if this
	 * is a private message.
	 */
	Group getGroup();

	/**
	 * Returns the message's {@link Author}, or null if this is an anonymous
	 * message.
	 */
	Author getAuthor();

	/** Returns the message's subject line. */
	String getSubject();

	/** Returns the timestamp created by the message's {@link Author}. */
	long getTimestamp();

	/** Returns the serialised message. */
	byte[] getSerialised();

	/**
	 * Returns the author's signature, or null if this is an anonymous message.
	 */
	byte[] getAuthorSignature();

	/**
	 * Returns the group's signature, or null if this is a private message or
	 * a message belonging to an unrestricted group.
	 */
	byte[] getGroupSignature();

	/** Returns the offset of the message body within the serialised message. */
	int getBodyStart();

	/** Returns the length of the message body in bytes. */
	int getBodyLength();

	/**
	 * Returns the length in bytes of the data covered by the author's
	 * signature.
	 */
	int getLengthSignedByAuthor();

	/**
	 * Returns the length in bytes of the data covered by the group's
	 * signature.
	 */
	int getLengthSignedByGroup();
}