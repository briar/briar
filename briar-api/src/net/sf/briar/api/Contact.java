package net.sf.briar.api;

public class Contact {

	private final ContactId id;
	private final Author author;
	private final long lastConnected;

	public Contact(ContactId id, Author author, long lastConnected) {
		this.id = id;
		this.author = author;
		this.lastConnected = lastConnected;
	}

	public ContactId getId() {
		return id;
	}

	public Author getAuthor() {
		return author;
	}

	public long getLastConnected() {
		return lastConnected;
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
