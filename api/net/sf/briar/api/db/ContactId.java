package net.sf.briar.api.db;

/** Type-safe wrapper for an integer that uniquely identifies a contact. */
public class ContactId {

	private final int id;

	public ContactId(int id) {
		this.id = id;
	}

	public int getInt() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof ContactId) return id == ((ContactId) o).id;
		return false;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		return String.valueOf(id);
	}
}
