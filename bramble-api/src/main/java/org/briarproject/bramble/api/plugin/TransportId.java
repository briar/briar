package org.briarproject.bramble.api.plugin;

import java.nio.charset.Charset;

/**
 * Type-safe wrapper for a string that uniquely identifies a transport plugin.
 */
public class TransportId {

	/**
	 * The maximum length of transport identifier in UTF-8 bytes.
	 */
	public static int MAX_TRANSPORT_ID_LENGTH = 64;

	private final String id;

	public TransportId(String id) {
		byte[] b = id.getBytes(Charset.forName("UTF-8"));
		if (b.length == 0 || b.length > MAX_TRANSPORT_ID_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
	}

	public String getString() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TransportId && id.equals(((TransportId) o).id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id;
	}
}
