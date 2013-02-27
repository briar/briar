package net.sf.briar.api;

public class Contact {

	private final ContactId id;
	private final String name;
	private final long lastConnected;

	public Contact(ContactId id, String name, long lastConnected) {
		this.id = id;
		this.name = name;
		this.lastConnected = lastConnected;
	}

	public ContactId getId() {
		return id;
	}

	public String getName() {
		return name;
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
