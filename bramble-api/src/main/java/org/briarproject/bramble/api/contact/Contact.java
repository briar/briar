package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Contact {

	private final ContactId id;
	private final Author author;
	private final AuthorId localAuthorId;
	private final boolean verified, active;

	public Contact(ContactId id, Author author, AuthorId localAuthorId,
			boolean verified, boolean active) {
		this.id = id;
		this.author = author;
		this.localAuthorId = localAuthorId;
		this.verified = verified;
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

	public boolean isVerified() {
		return verified;
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
