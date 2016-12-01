package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.OutputStream;

@NotNullByDefault
public interface StreamWriterFactory {

	/**
	 * Creates an {@link OutputStream OutputStream} for writing to a
	 * transport stream
	 */
	OutputStream createStreamWriter(OutputStream out, StreamContext ctx);

	/**
	 * Creates an {@link OutputStream OutputStream} for writing to an
	 * invitation stream.
	 */
	OutputStream createInvitationStreamWriter(OutputStream out,
			SecretKey headerKey);
}
