package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;
import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.transport.StreamContext;

class StreamEncrypterFactoryImpl implements StreamEncrypterFactory {

	private final CryptoComponent crypto;

	@Inject
	StreamEncrypterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public StreamEncrypter createStreamEncrypter(OutputStream out,
			int maxFrameLength, StreamContext ctx) {
		byte[] secret = ctx.getSecret();
		long streamNumber = ctx.getStreamNumber();
		boolean alice = ctx.getAlice();
		// Encode the tag
		byte[] tag = new byte[TAG_LENGTH];
		SecretKey tagKey = crypto.deriveTagKey(secret, alice);
		crypto.encodeTag(tag, tagKey, streamNumber);
		tagKey.erase();
		// Derive the frame key
		SecretKey frameKey = crypto.deriveFrameKey(secret, streamNumber, alice);
		// Create the encrypter
		return new StreamEncrypterImpl(out, crypto.getFrameCipher(), frameKey,
				maxFrameLength, tag);
	}

	public StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice) {
		// Derive the frame key
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, alice);
		// Create the encrypter
		return new StreamEncrypterImpl(out, crypto.getFrameCipher(), frameKey,
				maxFrameLength, null);
	}
}
