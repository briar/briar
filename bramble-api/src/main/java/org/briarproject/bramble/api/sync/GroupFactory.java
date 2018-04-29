package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface GroupFactory {

	/**
	 * Creates a group with the given client ID, major version and descriptor.
	 */
	Group createGroup(ClientId c, int majorVersion, byte[] descriptor);
}
