package net.sf.briar.api.protocol;

import java.security.GeneralSecurityException;

public interface MessageVerifier {

	Message verifyMessage(UnverifiedMessage m) throws GeneralSecurityException;
}
