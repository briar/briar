package org.briarproject.api;

public class Contact {

	private final ContactId id;
	private final Author author;
	private final AuthorId localAuthorId;

	public Contact(ContactId id, Author author, AuthorId localAuthorId) {
		this.id = id;
		this.author = author;
		this.localAuthorId = localAuthorId;
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

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Contact) return id.equals(((Contact) o).id);
		return false;
	}
}
