package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.OutputStream;

import javax.inject.Inject;
import javax.inject.Provider;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;
import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.transport.StreamContext;

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
		byte[] tag = new byte[TAG_LENGTH];
		crypto.encodeTag(tag, ctx.getTagKey(), ctx.getStreamNumber());
		AuthenticatedCipher cipher = cipherProvider.get();
		return new StreamEncrypterImpl(out, cipher, ctx.getHeaderKey(), tag);
	}

	public StreamEncrypter createInvitationStreamEncrypter(OutputStream out,
			SecretKey headerKey) {
		AuthenticatedCipher cipher = cipherProvider.get();
		return new StreamEncrypterImpl(out, cipher, headerKey, null);
	}
}
