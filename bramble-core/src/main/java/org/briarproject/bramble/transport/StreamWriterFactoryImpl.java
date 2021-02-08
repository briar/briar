package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.StreamEncrypterFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.OutputStream;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class StreamWriterFactoryImpl implements StreamWriterFactory {

	private final StreamEncrypterFactory streamEncrypterFactory;

	@Inject
	StreamWriterFactoryImpl(StreamEncrypterFactory streamEncrypterFactory) {
		this.streamEncrypterFactory = streamEncrypterFactory;
	}

	@Override
	public StreamWriter createStreamWriter(OutputStream out,
			StreamContext ctx) {
		return new StreamWriterImpl(streamEncrypterFactory
				.createStreamEncrypter(out, ctx));
	}

	@Override
	public StreamWriter createContactExchangeStreamWriter(OutputStream out,
			SecretKey headerKey) {
		return new StreamWriterImpl(streamEncrypterFactory
				.createContactExchangeStreamEncrypter(out, headerKey));
	}

	@Override
	public StreamWriter createLogStreamWriter(OutputStream out,
			SecretKey headerKey) {
		return new StreamWriterImpl(streamEncrypterFactory
				.createLogStreamEncrypter(out, headerKey));
	}
}
