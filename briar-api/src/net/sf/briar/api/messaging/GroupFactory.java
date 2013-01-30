package net.sf.briar.api.messaging;

import java.io.IOException;

public interface GroupFactory {

	Group createGroup(String name, byte[] publicKey) throws IOException;
}
