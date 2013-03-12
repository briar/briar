package net.sf.briar.android.groups;

import net.sf.briar.api.Rating;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.MessageId;

// This class is not thread-safe
class GroupItem {

	private final GroupMessageHeader header;
	private Rating rating;

	GroupItem(GroupMessageHeader header, Rating rating) {
		this.header = header;
		this.rating = rating;
	}

	MessageId getId() {
		return header.getId();
	}

	Author getAuthor() {
		return header.getAuthor();
	}

	String getContentType() {
		return header.getContentType();
	}

	String getSubject() {
		return header.getSubject();
	}

	long getTimestamp() {
		return header.getTimestamp();
	}

	boolean isRead() {
		return header.isRead();
	}

	Rating getRating() {
		return rating;
	}

	void setRating(Rating rating) {
		this.rating = rating;
	}
}
