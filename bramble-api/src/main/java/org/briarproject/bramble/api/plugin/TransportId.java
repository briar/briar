package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.util.StringUtils;

/**
 * Type-safe wrapper for a namespaced string that uniquely identifies a
 * transport plugin.
 */
public class TransportId {

	/**
	 * The maximum length of a transport identifier in UTF-8 bytes.
	 */
	public static int MAX_TRANSPORT_ID_LENGTH = 100;

	private final String id;

	public TransportId(String id) {
		int length = StringUtils.toUtf8(id).length;
		if (length == 0 || length > MAX_TRANSPORT_ID_LENGTH)
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
