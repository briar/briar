package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

@Immutable
@NotNullByDefault
public class Contact {

	private final ContactId id;
	private final Author author;
	private final AuthorId localAuthorId;
	@Nullable
	private final String alias;
	private final boolean verified, active;

	public Contact(ContactId id, Author author, AuthorId localAuthorId,
			@Nullable String alias, boolean verified, boolean active) {
		if (alias != null) {
			int aliasLength = toUtf8(alias).length;
			if (aliasLength == 0 || aliasLength > MAX_AUTHOR_NAME_LENGTH)
				throw new IllegalArgumentException();
		}
		this.id = id;
		this.author = author;
		this.localAuthorId = localAuthorId;
		this.alias = alias;
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

	@Nullable
	public String getAlias() {
		return alias;
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
