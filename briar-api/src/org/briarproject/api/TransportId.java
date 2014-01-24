package org.briarproject.api;

import static org.briarproject.api.TransportPropertyConstants.MAX_TRANSPORT_ID_LENGTH;

/**
 * Type-safe wrapper for a string that uniquely identifies a transport plugin.
 */
public class TransportId {

	private final String id;

	public TransportId(String id) {
		if(id.length() > MAX_TRANSPORT_ID_LENGTH || id.equals(""))
			throw new IllegalArgumentException();
		this.id = id;
	}

	public String getString() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TransportId) return id.equals(((TransportId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
