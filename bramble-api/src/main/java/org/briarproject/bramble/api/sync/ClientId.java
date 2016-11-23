package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for a name-spaced string that uniquely identifies a sync client.
 */
@Immutable
@NotNullByDefault
public class ClientId implements Comparable<ClientId> {

	private final String id;

	public ClientId(String id) {
		this.id = id;
	}

	public String getString() {
		return id;
	}

	@Override
	public int compareTo(ClientId clientId) {
		return id.compareTo(clientId.getString());
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ClientId && id.equals(((ClientId) o).id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

}
