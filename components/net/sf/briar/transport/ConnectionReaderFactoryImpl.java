package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.TransportId;
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
			byte[] encryptedIv, byte[] secret) {
		// Create the decrypter
		Cipher ivCipher = crypto.getIvCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		SecretKey ivKey = crypto.deriveIncomingIvKey(secret);
		SecretKey frameKey = crypto.deriveIncomingFrameKey(secret);
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in,
				encryptedIv, ivCipher, frameCipher, ivKey, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		SecretKey macKey = crypto.deriveIncomingMacKey(secret);
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			boolean initiator, TransportId t, long connection, byte[] secret) {
		byte[] iv = IvEncoder.encodeIv(initiator, t, connection);
		// Create the decrypter
		Cipher frameCipher = crypto.getFrameCipher();
		SecretKey frameKey = crypto.deriveIncomingFrameKey(secret);
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in, iv,
				frameCipher, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		SecretKey macKey = crypto.deriveIncomingMacKey(secret);
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
