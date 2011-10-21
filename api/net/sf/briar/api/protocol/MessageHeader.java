package net.sf.briar.api.protocol;

public interface MessageHeader {

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

	/** Returns the message's subject line. */
	String getSubject();

	/** Returns the timestamp created by the message's author. */
	long getTimestamp();
}
