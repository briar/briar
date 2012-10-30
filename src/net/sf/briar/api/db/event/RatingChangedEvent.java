package net.sf.briar.api.db.event;

import net.sf.briar.api.Rating;
import net.sf.briar.api.protocol.AuthorId;

public class RatingChangedEvent extends DatabaseEvent {

	private final AuthorId author;
	private final Rating rating;

	public RatingChangedEvent(AuthorId author, Rating rating) {
		this.author = author;
		this.rating = rating;
	}

	public AuthorId getAuthor() {
		return author;
	}

	public Rating getRating() {
		return rating;
	}
}
