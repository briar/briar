package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface MessageParser {

	Message parseMessage(byte[] raw) throws IOException, GeneralSecurityException;
}
