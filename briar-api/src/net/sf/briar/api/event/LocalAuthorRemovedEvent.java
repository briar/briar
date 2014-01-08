package net.sf.briar.api.event;

import net.sf.briar.api.AuthorId;

/** An event that is broadcast when a local pseudonym is removed. */
public class LocalAuthorRemovedEvent extends Event {

	private final AuthorId authorId;

	public LocalAuthorRemovedEvent(AuthorId authorId) {
		this.authorId = authorId;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}
}
