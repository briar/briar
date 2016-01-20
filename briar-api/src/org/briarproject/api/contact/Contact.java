package org.briarproject.api.contact;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

public class Contact {

	public enum Status {

		ADDING(0), ACTIVE(1), REMOVING(2);

		private final int value;

		Status(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static Status fromValue(int value) {
			for (Status s : values()) if (s.value == value) return s;
			throw new IllegalArgumentException();
		}
	}

	private final ContactId id;
	private final Author author;
	private final AuthorId localAuthorId;
	private final Status status;

	public Contact(ContactId id, Author author, AuthorId localAuthorId,
			Status status) {
		this.id = id;
		this.author = author;
		this.localAuthorId = localAuthorId;
		this.status = status;
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

	public Status getStatus() {
		return status;
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
