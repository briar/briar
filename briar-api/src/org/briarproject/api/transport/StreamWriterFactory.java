package org.briarproject.api.transport;

import java.io.OutputStream;

import org.briarproject.api.crypto.SecretKey;

public interface StreamWriterFactory {

	/**
	 * Creates an {@link java.io.OutputStream OutputStream} for writing to a
	 * transport stream
	 */
	OutputStream createStreamWriter(OutputStream out, StreamContext ctx);

	/**
	 * Creates an {@link java.io.OutputStream OutputStream} for writing to an
	 * invitation stream.
	 */
	OutputStream createInvitationStreamWriter(OutputStream out,
			SecretKey headerKey);
}
