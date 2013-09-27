package net.sf.briar.api.messaging;

import java.io.IOException;

public interface GroupFactory {

	/** Creates a group with the given name and a random salt. */
	Group createGroup(String name) throws IOException;

	/** Creates a group with the given name and salt. */
	Group createGroup(String name, byte[] salt) throws IOException;
}
