package net.sf.briar.api.protocol;

import java.io.IOException;

public interface GroupFactory {

	Group createGroup(String name, byte[] publicKey) throws IOException;
}
