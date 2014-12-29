package org.briarproject.transport;

import java.io.InputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.StreamDecrypter;
import org.briarproject.api.crypto.StreamDecrypterFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;

class StreamReaderFactoryImpl implements StreamReaderFactory {

	private final StreamDecrypterFactory streamDecrypterFactory;

	@Inject
	StreamReaderFactoryImpl(StreamDecrypterFactory streamDecrypterFactory) {
		this.streamDecrypterFactory = streamDecrypterFactory;
	}

	public InputStream createStreamReader(InputStream in, int maxFrameLength,
			StreamContext ctx) {
		StreamDecrypter s = streamDecrypterFactory.createStreamDecrypter(in,
				maxFrameLength, ctx);
		return new StreamReaderImpl(s, maxFrameLength);
	}

	public InputStream createInvitationStreamReader(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice) {
		StreamDecrypter s =
				streamDecrypterFactory.createInvitationStreamDecrypter(in,
						maxFrameLength, secret, alice);
		return new StreamReaderImpl(s, maxFrameLength);
	}
}
