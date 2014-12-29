package org.briarproject.api.crypto;

import java.io.InputStream;

import org.briarproject.api.transport.StreamContext;

public interface StreamDecrypterFactory {

	/** Creates a {@link StreamDecrypter} for decrypting a transport stream. */
	StreamDecrypter createStreamDecrypter(InputStream in, int maxFrameLength,
			StreamContext ctx);

	/**
	 * Creates a {@link StreamDecrypter} for decrypting an invitation stream.
	 */
	StreamDecrypter createInvitationStreamDecrypter(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice);
}
