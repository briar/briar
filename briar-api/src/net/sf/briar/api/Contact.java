package net.sf.briar.api;

public class Contact {

	private final ContactId id;
	private final String name;

	public Contact(ContactId id, String name) {
		this.id = id;
		this.name = name;
	}

	public ContactId getId() {
		return id;
	}

	public String getName() {
		return name;
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
