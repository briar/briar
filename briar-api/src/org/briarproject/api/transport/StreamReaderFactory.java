package org.briarproject.api.transport;

import java.io.InputStream;

public interface StreamReaderFactory {

	/** Creates a {@link StreamReader} for a transport connection. */
	StreamReader createStreamReader(InputStream in, int maxFrameLength,
			StreamContext ctx, boolean incoming, boolean initiator);

	/** Creates a {@link StreamReader} for an invitation connection. */
	StreamReader createInvitationStreamReader(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice);
}
