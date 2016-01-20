package org.briarproject.api.sync;

public interface GroupFactory {

	/** Creates a group with the given client ID and descriptor. */
	Group createGroup(ClientId c, byte[] descriptor);
}
