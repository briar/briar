package net.sf.briar.api.protocol;

import java.security.SignatureException;

import net.sf.briar.api.serial.FormatException;

public interface MessageParser {

	Message parseMessage(byte[] body) throws FormatException, SignatureException;
}
