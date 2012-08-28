package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.InputStream;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionReaderFactoryImpl implements ConnectionReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionReader createConnectionReader(InputStream in,
			byte[] secret, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the reader
		Cipher tagCipher = crypto.getTagCipher();
		AuthenticatedCipher frameCipher = crypto.getFrameCipher();
		FrameReader encryption = new IncomingEncryptionLayer(in, tagCipher,
				frameCipher, tagKey, frameKey, !initiator, MAX_FRAME_LENGTH);
		return new ConnectionReaderImpl(encryption, MAX_FRAME_LENGTH);
	}
}
