package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Map;

public interface HeaderBuilder {

	/** Adds acknowledgements to the header. */
	void addAcks(Iterable<BatchId> acks);

	/** Adds subscriptions to the header. */
	void addSubscriptions(Iterable<GroupId> subs);

	/** Adds transport details to the header. */
	void addTransports(Map<String, String> transports);

	/** Sets the sender's signature over the contents of the header. */
	void setSignature(byte[] sig);

	/** Builds and returns the header. */
	Header build() throws IOException, SignatureException, InvalidKeyException;
}
