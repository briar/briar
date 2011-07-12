package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

public interface MessageEncoder {

	Message encodeMessage(MessageId parent, GroupId group, String nick,
			KeyPair keyPair, byte[] body) throws IOException,
			GeneralSecurityException;
}
