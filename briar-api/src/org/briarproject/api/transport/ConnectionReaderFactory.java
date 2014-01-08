package org.briarproject.api.transport;

import java.io.InputStream;

public interface ConnectionReaderFactory {

	/** Creates a connection reader for one side of a connection. */
	ConnectionReader createConnectionReader(InputStream in, int maxFrameLength,
			ConnectionContext ctx, boolean incoming, boolean initiator);

	/** Creates a connection reader for one side of an invitation connection. */
	ConnectionReader createInvitationConnectionReader(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice);
}
