package net.sf.briar.api.db;

public class NeighbourId {

	private final int id;

	public NeighbourId(int id) {
		this.id = id;
	}

	public int getInt() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof NeighbourId) return id == ((NeighbourId) o).id;
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
