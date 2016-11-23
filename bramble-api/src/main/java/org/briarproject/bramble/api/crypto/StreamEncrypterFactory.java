package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamContext;

import java.io.OutputStream;

@NotNullByDefault
public interface StreamEncrypterFactory {

	/**
	 * Creates a {@link StreamEncrypter} for encrypting a transport stream.
	 */
	StreamEncrypter createStreamEncrypter(OutputStream out, StreamContext ctx);

	/**
	 * Creates a {@link StreamEncrypter} for encrypting an invitation stream.
	 */
	StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			SecretKey headerKey);
}
