package org.briarproject.api.messaging;

public interface GroupFactory {

	/** Creates a group with the given name and a random salt. */
	Group createGroup(String name);

	/** Creates a group with the given name and salt. */
	Group createGroup(String name, byte[] salt);
}
