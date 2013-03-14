package net.sf.briar.api.db;

import net.sf.briar.api.Rating;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;

public class GroupMessageHeader extends MessageHeader {

	private final GroupId groupId;
	private final Author author;
	private final Rating rating;

	public GroupMessageHeader(MessageId id, MessageId parent,
			String contentType, String subject, long timestamp, boolean read,
			boolean starred, GroupId groupId, Author author, Rating rating) {
		super(id, parent, contentType, subject, timestamp, read, starred);
		this.groupId = groupId;
		this.author = author;
		this.rating = rating;
	}

	public GroupMessageHeader(Message m, boolean read, boolean starred,
			Rating rating) {
		this(m.getId(), m.getParent(), m.getContentType(), m.getSubject(),
				m.getTimestamp(), read, starred, m.getGroup().getId(),
				m.getAuthor(), rating);
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

	/**
	 * Returns the rating for the message's author, or Rating.UNRATED if this
	 * is an anonymous message.
	 */
	public Rating getRating() {
		return rating;
	}
}
