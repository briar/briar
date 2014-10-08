package org.briarproject.api.transport;

import java.io.OutputStream;

public interface StreamWriterFactory {

	/** Creates a {@link StreamWriter} for a transport connection. */
	StreamWriter createStreamWriter(OutputStream out, int maxFrameLength,
			long capacity, StreamContext ctx, boolean incoming,
			boolean initiator);

	/** Creates a {@link StreamWriter} for an invitation connection. */
	StreamWriter createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice);
}
