package org.briarproject.bramble.api.identity.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a local pseudonym is removed.
 */
@Immutable
@NotNullByDefault
public class LocalAuthorRemovedEvent extends Event {

	private final AuthorId authorId;

	public LocalAuthorRemovedEvent(AuthorId authorId) {
		this.authorId = authorId;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}
}
