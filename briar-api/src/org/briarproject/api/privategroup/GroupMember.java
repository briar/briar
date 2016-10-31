package org.briarproject.api.privategroup;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMember {

	private final Author author;
	private final Status status;
	private final boolean shared;

	public GroupMember(Author author, Status status, boolean shared) {
		this.author = author;
		this.status = status;
		this.shared = shared;
	}

	public Author getAuthor() {
		return author;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isShared() {
		return shared;
	}

}
