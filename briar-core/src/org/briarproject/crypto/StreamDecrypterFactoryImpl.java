package org.briarproject.crypto;

import java.io.InputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamDecrypter;
import org.briarproject.api.crypto.StreamDecrypterFactory;
import org.briarproject.api.transport.StreamContext;

class StreamDecrypterFactoryImpl implements StreamDecrypterFactory {

	private final CryptoComponent crypto;

	@Inject
	StreamDecrypterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public StreamDecrypter createStreamDecrypter(InputStream in,
			int maxFrameLength, StreamContext ctx) {
		byte[] secret = ctx.getSecret();
		long streamNumber = ctx.getStreamNumber();
		boolean alice = !ctx.getAlice();
		// Derive the frame key
		SecretKey frameKey = crypto.deriveFrameKey(secret, streamNumber, alice);
		// Create the decrypter
		return new StreamDecrypterImpl(in, crypto.getFrameCipher(), frameKey,
				maxFrameLength);
	}

	public StreamDecrypter createInvitationStreamDecrypter(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice) {
		// Derive the frame key
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, alice);
		// Create the decrypter
		return new StreamDecrypterImpl(in, crypto.getFrameCipher(), frameKey,
				maxFrameLength);
	}
}
