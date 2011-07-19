package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;

import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.serial.Reader;

interface MessageReader {

	Message readMessage(Reader r) throws IOException,
	GeneralSecurityException;
}
