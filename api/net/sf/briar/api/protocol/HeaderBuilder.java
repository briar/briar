package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Map;
import java.util.Set;

public interface HeaderBuilder {

	/** Adds acknowledgements to the header. */
	void addAcks(Set<BatchId> acks) throws IOException;

	/** Adds subscriptions to the header. */
	void addSubscriptions(Set<GroupId> subs) throws IOException;

	/** Adds transport details to the header. */
	void addTransports(Map<String, String> transports) throws IOException;

	/** Sets the sender's signature over the contents of the header. */
	void setSignature(byte[] sig) throws IOException;

	/** Builds and returns the header. */
	Header build() throws SignatureException;
}
