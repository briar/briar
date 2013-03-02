package net.sf.briar.api.db;

import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.MessageId;

public class GroupMessageHeader extends MessageHeader {

	private final GroupId groupId;
	private final Author author;

	public GroupMessageHeader(MessageId id, MessageId parent,
			String contentType, String subject, long timestamp, boolean read,
			boolean starred, GroupId groupId, Author author) {
		super(id, parent, contentType, subject, timestamp, read, starred);
		this.groupId = groupId;
		this.author = author;
	}

	/** Returns the ID of the group to which the message belongs. */
	public GroupId getGroupId() {
		return groupId;
	}

	/**
	 * Returns the message's author, or null if this is an  anonymous message.
	 */
	public Author getAuthor() {
		return author;
	}
}
