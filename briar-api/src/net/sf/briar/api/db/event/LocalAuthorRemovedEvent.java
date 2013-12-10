package net.sf.briar.api.db.event;

import net.sf.briar.api.AuthorId;

/** An event that is broadcast when a pseudonym for the user is removed. */
public class LocalAuthorRemovedEvent extends DatabaseEvent {

	private final AuthorId authorId;

	public LocalAuthorRemovedEvent(AuthorId authorId) {
		this.authorId = authorId;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}
}
