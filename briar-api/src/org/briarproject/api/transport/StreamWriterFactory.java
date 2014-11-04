package org.briarproject.api.transport;

import java.io.OutputStream;

public interface StreamWriterFactory {

	/** Creates a {@link StreamWriter} for a transport connection. */
	StreamWriter createStreamWriter(OutputStream out, int maxFrameLength,
			StreamContext ctx);

	/** Creates a {@link StreamWriter} for an invitation connection. */
	StreamWriter createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice);
}
