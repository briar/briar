package org.briarproject.api.event;

import org.briarproject.api.identity.AuthorId;

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
