package org.briarproject.transport;

import java.io.InputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReader;
import org.briarproject.api.transport.StreamReaderFactory;

class StreamReaderFactoryImpl implements StreamReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	StreamReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public StreamReader createStreamReader(InputStream in,
			int maxFrameLength, StreamContext ctx, boolean incoming,
			boolean initiator) {
		byte[] secret = ctx.getSecret();
		long streamNumber = ctx.getStreamNumber();
		boolean weAreAlice = ctx.getAlice();
		boolean initiatorIsAlice = incoming ? !weAreAlice : weAreAlice;
		SecretKey frameKey = crypto.deriveFrameKey(secret, streamNumber,
				initiatorIsAlice, initiator);
		FrameReader encryption = new IncomingEncryptionLayer(in,
				crypto.getFrameCipher(), frameKey, maxFrameLength);
		return new StreamReaderImpl(encryption, maxFrameLength);
	}

	public StreamReader createInvitationStreamReader(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice) {
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, true, alice);
		FrameReader encryption = new IncomingEncryptionLayer(in,
				crypto.getFrameCipher(), frameKey, maxFrameLength);
		return new StreamReaderImpl(encryption, maxFrameLength);
	}
}
