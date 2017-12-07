package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface GroupFactory {

	/**
	 * Creates a group with the given client ID, client version and descriptor.
	 */
	Group createGroup(ClientId c, int clientVersion, byte[] descriptor);
}
