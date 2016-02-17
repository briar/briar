package org.briarproject.api.contact;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

public class Contact {

	private final ContactId id;
	private final Author author;
	private final AuthorId localAuthorId;
	private final boolean active;

	public Contact(ContactId id, Author author, AuthorId localAuthorId,
			boolean active) {
		this.id = id;
		this.author = author;
		this.localAuthorId = localAuthorId;
		this.active = active;
	}

	public ContactId getId() {
		return id;
	}

	public Author getAuthor() {
		return author;
	}

	public AuthorId getLocalAuthorId() {
		return localAuthorId;
	}

	public boolean isActive() {
		return active;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Contact && id.equals(((Contact) o).id);
	}
}
