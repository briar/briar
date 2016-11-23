package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface StreamReaderFactory {

	/**
	 * Creates an {@link InputStream InputStream} for reading from a
	 * transport stream.
	 */
	InputStream createStreamReader(InputStream in, StreamContext ctx);

	/**
	 * Creates an {@link InputStream InputStream} for reading from an
	 * invitation stream.
	 */
	InputStream createInvitationStreamReader(InputStream in,
			SecretKey headerKey);
}
