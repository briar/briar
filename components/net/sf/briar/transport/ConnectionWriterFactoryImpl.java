package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.OutputStream;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, byte[] secret, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the writer
		Cipher tagCipher = crypto.getTagCipher();
		AuthenticatedCipher frameCipher = crypto.getFrameCipher();
		FrameWriter encryption = new OutgoingEncryptionLayer(out, capacity,
				tagCipher, frameCipher, tagKey, frameKey, initiator,
				MAX_FRAME_LENGTH);
		return new ConnectionWriterImpl(encryption, MAX_FRAME_LENGTH);
	}
}
