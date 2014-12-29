package org.briarproject.api.crypto;

import java.io.OutputStream;

import org.briarproject.api.transport.StreamContext;

public interface StreamEncrypterFactory {

	/** Creates a {@link StreamEncrypter} for encrypting a transport stream. */
	StreamEncrypter createStreamEncrypter(OutputStream out,
			int maxFrameLength, StreamContext ctx);

	/**
	 * Creates a {@link StreamEncrypter} for encrypting an invitation stream.
	 */
	StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice);
}
