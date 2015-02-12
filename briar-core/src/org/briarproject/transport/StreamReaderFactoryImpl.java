package org.briarproject.transport;

import java.io.InputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamDecrypterFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;

class StreamReaderFactoryImpl implements StreamReaderFactory {

	private final StreamDecrypterFactory streamDecrypterFactory;

	@Inject
	StreamReaderFactoryImpl(StreamDecrypterFactory streamDecrypterFactory) {
		this.streamDecrypterFactory = streamDecrypterFactory;
	}

	public InputStream createStreamReader(InputStream in, StreamContext ctx) {
		return new StreamReaderImpl(
				streamDecrypterFactory.createStreamDecrypter(in, ctx));
	}

	public InputStream createInvitationStreamReader(InputStream in,
			SecretKey headerKey) {
		return new StreamReaderImpl(
				streamDecrypterFactory.createInvitationStreamDecrypter(in,
						headerKey));
	}
}
