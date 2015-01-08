package org.briarproject.transport;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriterFactory;

class StreamWriterFactoryImpl implements StreamWriterFactory {

	private final StreamEncrypterFactory streamEncrypterFactory;

	@Inject
	StreamWriterFactoryImpl(StreamEncrypterFactory streamEncrypterFactory) {
		this.streamEncrypterFactory = streamEncrypterFactory;
	}

	public OutputStream createStreamWriter(OutputStream out,
			StreamContext ctx) {
		return new StreamWriterImpl(
				streamEncrypterFactory.createStreamEncrypter(out, ctx));
	}

	public OutputStream createInvitationStreamWriter(OutputStream out,
			byte[] secret, boolean alice) {
		return new StreamWriterImpl(
				streamEncrypterFactory.createInvitationStreamEncrypter(out,
						secret, alice));
	}
}