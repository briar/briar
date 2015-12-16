package org.briarproject.api.event;

import org.briarproject.api.identity.AuthorId;

/** An event that is broadcast when a local pseudonym is added. */
public class LocalAuthorAddedEvent extends Event {

	private final AuthorId authorId;

	public LocalAuthorAddedEvent(AuthorId authorId) {
		this.authorId = authorId;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}
}
