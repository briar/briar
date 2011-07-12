package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

public interface BatchBuilder {

	/** Adds a message to the batch. */
	void addMessage(Message m);

	/** Sets the sender's signature over the contents of the batch. */
	void setSignature(byte[] sig);

	/** Builds and returns the batch. */
	Batch build() throws IOException, SignatureException, InvalidKeyException;
}
