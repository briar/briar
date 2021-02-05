package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamContext;

import java.io.InputStream;

@NotNullByDefault
public interface StreamDecrypterFactory {

	/**
	 * Creates a {@link StreamDecrypter} for decrypting a transport stream.
	 */
	StreamDecrypter createStreamDecrypter(InputStream in, StreamContext ctx);

	/**
	 * Creates a {@link StreamDecrypter} for decrypting a contact exchange
	 * stream.
	 */
	StreamDecrypter createContactExchangeStreamDecrypter(InputStream in,
			SecretKey headerKey);

	/**
	 * Creates a {@link StreamDecrypter} for decrypting a log stream.
	 */
	StreamDecrypter createLogStreamDecrypter(InputStream in,
			SecretKey headerKey);
}
