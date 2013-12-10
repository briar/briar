package net.sf.briar.api.db.event;

import net.sf.briar.api.AuthorId;

/** An event that is broadcast when a pseudonym for the user is added. */
public class LocalAuthorAddedEvent extends DatabaseEvent {

	private final AuthorId authorId;

	public LocalAuthorAddedEvent(AuthorId authorId) {
		this.authorId = authorId;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}
}
