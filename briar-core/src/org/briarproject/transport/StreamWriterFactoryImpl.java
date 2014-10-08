package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;

class StreamWriterFactoryImpl implements StreamWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	StreamWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public StreamWriter createStreamWriter(OutputStream out,
			int maxFrameLength, long capacity, StreamContext ctx,
			boolean incoming, boolean initiator) {
		byte[] secret = ctx.getSecret();
		long streamNumber = ctx.getStreamNumber();
		boolean weAreAlice = ctx.getAlice();
		boolean initiatorIsAlice = incoming ? !weAreAlice : weAreAlice;
		SecretKey frameKey = crypto.deriveFrameKey(secret, streamNumber,
				initiatorIsAlice, initiator);
		FrameWriter encryption;
		if(initiator) {
			byte[] tag = new byte[TAG_LENGTH];
			SecretKey tagKey = crypto.deriveTagKey(secret, initiatorIsAlice);
			crypto.encodeTag(tag, tagKey, streamNumber);
			tagKey.erase();
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, maxFrameLength, tag);
		} else {
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, maxFrameLength);
		}
		return new StreamWriterImpl(encryption, maxFrameLength);
	}

	public StreamWriter createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice) {
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, true, alice);
		FrameWriter encryption = new OutgoingEncryptionLayer(out,
				Long.MAX_VALUE, crypto.getFrameCipher(), frameKey,
				maxFrameLength);
		return new StreamWriterImpl(encryption, maxFrameLength);
	}
}