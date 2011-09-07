package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;

import com.google.inject.Inject;

class ConnectionReaderFactoryImpl implements ConnectionReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionReader createConnectionReader(InputStream in,
			boolean initiator, int transportId, long connection,
			byte[] secret) {
		SecretKey macKey = crypto.deriveIncomingMacKey(secret);
		SecretKey frameKey = crypto.deriveIncomingFrameKey(secret);
		Cipher frameCipher = crypto.getFrameCipher();
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in,
				initiator, transportId, connection, frameCipher, frameKey);
		return new ConnectionReaderImpl(decrypter, mac);
	}
}
