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
			int maxFrameLength, StreamContext ctx) {
		byte[] secret = ctx.getSecret();
		long streamNumber = ctx.getStreamNumber();
		boolean alice = ctx.getAlice();
		byte[] tag = new byte[TAG_LENGTH];
		SecretKey tagKey = crypto.deriveTagKey(secret, alice);
		crypto.encodeTag(tag, tagKey, streamNumber);
		tagKey.erase();
		SecretKey frameKey = crypto.deriveFrameKey(secret, streamNumber, alice);
		FrameWriter frameWriter = new OutgoingEncryptionLayer(out,
				crypto.getFrameCipher(), frameKey, maxFrameLength, tag);
		return new StreamWriterImpl(frameWriter, maxFrameLength);
	}

	public StreamWriter createInvitationStreamWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice) {
		byte[] tag = new byte[TAG_LENGTH];
		SecretKey tagKey = crypto.deriveTagKey(secret, alice);
		crypto.encodeTag(tag, tagKey, 0);
		tagKey.erase();
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, alice);
		FrameWriter frameWriter = new OutgoingEncryptionLayer(out,
				crypto.getFrameCipher(), frameKey, maxFrameLength, tag);
		return new StreamWriterImpl(frameWriter, maxFrameLength);
	}
}