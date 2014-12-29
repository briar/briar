package org.briarproject.api.transport;

import java.io.OutputStream;

public interface StreamWriterFactory {

	/**
	 * Creates an {@link java.io.OutputStream OutputStream} for writing to a
	 * transport stream
	 */
	OutputStream createStreamWriter(OutputStream out, int maxFrameLength,
			StreamContext ctx);

	/**
	 * Creates an {@link java.io.OutputStream OutputStream} for writing to an
	 * invitation stream.
	 */
	OutputStream createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice);
}
