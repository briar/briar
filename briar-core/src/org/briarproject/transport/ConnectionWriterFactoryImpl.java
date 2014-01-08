package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.OutputStream;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.transport.ConnectionContext;
import org.briarproject.api.transport.ConnectionWriter;
import org.briarproject.api.transport.ConnectionWriterFactory;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			int maxFrameLength, long capacity, ConnectionContext ctx,
			boolean incoming, boolean initiator) {
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean weAreAlice = ctx.getAlice();
		boolean initiatorIsAlice = incoming ? !weAreAlice : weAreAlice;
		SecretKey frameKey = crypto.deriveFrameKey(secret, connection,
				initiatorIsAlice, initiator);
		FrameWriter encryption;
		if(initiator) {
			byte[] tag = new byte[TAG_LENGTH];
			SecretKey tagKey = crypto.deriveTagKey(secret, initiatorIsAlice);
			crypto.encodeTag(tag, tagKey, connection);
			tagKey.erase();
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, maxFrameLength, tag);
		} else {
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, maxFrameLength);
		}
		return new ConnectionWriterImpl(encryption, maxFrameLength);
	}

	public ConnectionWriter createInvitationConnectionWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice) {
		SecretKey frameKey = crypto.deriveFrameKey(secret, 0, true, alice);
		FrameWriter encryption = new OutgoingEncryptionLayer(out,
				Long.MAX_VALUE, crypto.getFrameCipher(), frameKey,
				maxFrameLength);
		return new ConnectionWriterImpl(encryption, maxFrameLength);
	}
}