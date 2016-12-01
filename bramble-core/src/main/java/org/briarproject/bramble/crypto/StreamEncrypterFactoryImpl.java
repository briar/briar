package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.StreamEncrypter;
import org.briarproject.bramble.api.crypto.StreamEncrypterFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamContext;

import java.io.OutputStream;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Provider;

import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;

@Immutable
@NotNullByDefault
class StreamEncrypterFactoryImpl implements StreamEncrypterFactory {

	private final CryptoComponent crypto;
	private final Provider<AuthenticatedCipher> cipherProvider;

	@Inject
	StreamEncrypterFactoryImpl(CryptoComponent crypto,
			Provider<AuthenticatedCipher> cipherProvider) {
		this.crypto = crypto;
		this.cipherProvider = cipherProvider;
	}

	@Override
	public StreamEncrypter createStreamEncrypter(OutputStream out,
			StreamContext ctx) {
		AuthenticatedCipher cipher = cipherProvider.get();
		long streamNumber = ctx.getStreamNumber();
		byte[] tag = new byte[TAG_LENGTH];
		crypto.encodeTag(tag, ctx.getTagKey(), streamNumber);
		byte[] streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		crypto.getSecureRandom().nextBytes(streamHeaderIv);
		SecretKey frameKey = crypto.generateSecretKey();
		return new StreamEncrypterImpl(out, cipher, streamNumber, tag,
				streamHeaderIv, ctx.getHeaderKey(), frameKey);
	}

	@Override
	public StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			SecretKey headerKey) {
		AuthenticatedCipher cipher = cipherProvider.get();
		byte[] streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		crypto.getSecureRandom().nextBytes(streamHeaderIv);
		SecretKey frameKey = crypto.generateSecretKey();
		return new StreamEncrypterImpl(out, cipher, 0, null, streamHeaderIv,
				headerKey, frameKey);
	}
}
