package net.sf.briar.api.messaging;

import java.io.IOException;

public interface GroupFactory {

	/** Creates an unrestricted group. */
	Group createGroup(String name) throws IOException;

	/** Creates a restricted group. */
	Group createGroup(String name, byte[] publicKey) throws IOException;
}
