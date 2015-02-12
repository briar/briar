package org.briarproject.api.transport;

import java.io.InputStream;

import org.briarproject.api.crypto.SecretKey;

public interface StreamReaderFactory {

	/**
	 * Creates an {@link java.io.InputStream InputStream} for reading from a
	 * transport stream.
	 */
	InputStream createStreamReader(InputStream in, StreamContext ctx);

	/**
	 * Creates an {@link java.io.InputStream InputStream} for reading from an
	 * invitation stream.
	 */
	InputStream createInvitationStreamReader(InputStream in,
			SecretKey headerKey);
}
