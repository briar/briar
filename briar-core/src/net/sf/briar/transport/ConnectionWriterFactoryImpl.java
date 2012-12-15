package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.OutputStream;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;


import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, ConnectionContext ctx, boolean incoming,
			boolean initiator) {
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean weAreAlice = ctx.getAlice();
		boolean initiatorIsAlice = incoming ? !weAreAlice : weAreAlice;
		ErasableKey frameKey = crypto.deriveFrameKey(secret, connection,
				initiatorIsAlice, initiator);
		FrameWriter encryption;
		if(initiator) {
			byte[] tag = new byte[TAG_LENGTH];
			Cipher tagCipher = crypto.getTagCipher();
			ErasableKey tagKey = crypto.deriveTagKey(secret, initiatorIsAlice);
			crypto.encodeTag(tag, tagCipher, tagKey, connection);
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, MAX_FRAME_LENGTH, tag);
		} else {
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, MAX_FRAME_LENGTH);
		}
		return new ConnectionWriterImpl(encryption, MAX_FRAME_LENGTH);
	}
}