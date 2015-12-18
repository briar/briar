package org.briarproject.crypto;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;
import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.transport.StreamContext;

import java.io.OutputStream;

import javax.inject.Inject;
import javax.inject.Provider;

import static org.briarproject.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

class StreamEncrypterFactoryImpl implements StreamEncrypterFactory {

	private final CryptoComponent crypto;
	private final Provider<AuthenticatedCipher> cipherProvider;

	@Inject
	StreamEncrypterFactoryImpl(CryptoComponent crypto,
			Provider<AuthenticatedCipher> cipherProvider) {
		this.crypto = crypto;
		this.cipherProvider = cipherProvider;
	}

	public StreamEncrypter createStreamEncrypter(OutputStream out,
			StreamContext ctx) {
		AuthenticatedCipher cipher = cipherProvider.get();
		byte[] tag = new byte[TAG_LENGTH];
		crypto.encodeTag(tag, ctx.getTagKey(), ctx.getStreamNumber());
		byte[] streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		crypto.getSecureRandom().nextBytes(streamHeaderIv);
		SecretKey frameKey = crypto.generateSecretKey();
		return new StreamEncrypterImpl(out, cipher, tag, streamHeaderIv,
				ctx.getHeaderKey(), frameKey);
	}

	public StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			SecretKey headerKey) {
		AuthenticatedCipher cipher = cipherProvider.get();
		byte[] streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		crypto.getSecureRandom().nextBytes(streamHeaderIv);
		SecretKey frameKey = crypto.generateSecretKey();
		return new StreamEncrypterImpl(out, cipher, null, streamHeaderIv,
				headerKey, frameKey);
	}
}
