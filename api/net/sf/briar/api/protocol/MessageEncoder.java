package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public interface MessageEncoder {

	Message encodeMessage(MessageId parent, Group group, byte[] body)
	throws IOException;

	Message encodeMessage(MessageId parent, Group group, Author author,
			PrivateKey privateKey, byte[] body) throws IOException,
			GeneralSecurityException;
}
