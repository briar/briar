package org.briarproject.crypto;

import java.io.InputStream;

import javax.inject.Inject;
import javax.inject.Provider;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamDecrypter;
import org.briarproject.api.crypto.StreamDecrypterFactory;
import org.briarproject.api.transport.StreamContext;

class StreamDecrypterFactoryImpl implements StreamDecrypterFactory {

	private final Provider<AuthenticatedCipher> cipherProvider;

	@Inject
	StreamDecrypterFactoryImpl(Provider<AuthenticatedCipher> cipherProvider) {
		this.cipherProvider = cipherProvider;
	}

	public StreamDecrypter createStreamDecrypter(InputStream in,
			StreamContext ctx) {
		AuthenticatedCipher cipher = cipherProvider.get();
		return new StreamDecrypterImpl(in, cipher, ctx.getHeaderKey());
	}

	public StreamDecrypter createInvitationStreamDecrypter(InputStream in,
			SecretKey headerKey) {
		return new StreamDecrypterImpl(in, cipherProvider.get(), headerKey);
	}
}
