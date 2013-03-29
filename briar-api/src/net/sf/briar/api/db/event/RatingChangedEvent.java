package net.sf.briar.api.db.event;

import net.sf.briar.api.AuthorId;
import net.sf.briar.api.messaging.Rating;

public class RatingChangedEvent extends DatabaseEvent {

	private final AuthorId author;
	private final Rating rating;

	public RatingChangedEvent(AuthorId author, Rating rating) {
		this.author = author;
		this.rating = rating;
	}

	public AuthorId getAuthorId() {
		return author;
	}

	public Rating getRating() {
		return rating;
	}
}
