package net.sf.briar.api.protocol;

import net.sf.briar.api.serial.Raw;

public interface Message extends Raw {

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
}