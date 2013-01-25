package net.sf.briar.api.protocol;

import java.security.GeneralSecurityException;

/** Verifies the signatures on an {@link UnverifiedMessage}. */
public interface MessageVerifier {

	Message verifyMessage(UnverifiedMessage m) throws GeneralSecurityException;
}
