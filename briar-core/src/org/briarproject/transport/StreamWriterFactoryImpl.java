package org.briarproject.transport;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.StreamEncrypter;
import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriterFactory;

class StreamWriterFactoryImpl implements StreamWriterFactory {

	private final StreamEncrypterFactory streamEncrypterFactory;

	@Inject
	StreamWriterFactoryImpl(StreamEncrypterFactory streamEncrypterFactory) {
		this.streamEncrypterFactory = streamEncrypterFactory;
	}

	public OutputStream createStreamWriter(OutputStream out, int maxFrameLength,
			StreamContext ctx) {
		StreamEncrypter s = streamEncrypterFactory.createStreamEncrypter(out,
				maxFrameLength, ctx);
		return new StreamWriterImpl(s, maxFrameLength);
	}

	public OutputStream createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice) {
		StreamEncrypter s =
				streamEncrypterFactory.createInvitationStreamEncrypter(out,
						maxFrameLength, secret, alice);
		return new StreamWriterImpl(s, maxFrameLength);
	}
}