package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
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
			TransportId t, byte[] encryptedIv, byte[] secret) {
		// Decrypt the IV
		Cipher ivCipher = crypto.getIvCipher();
		SecretKey ivKey = crypto.deriveIncomingIvKey(secret);
		byte[] iv;
		try {
			ivCipher.init(Cipher.DECRYPT_MODE, ivKey);
			iv = ivCipher.doFinal(encryptedIv);
		} catch(BadPaddingException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		// Validate the IV
		if(!IvEncoder.validateIv(iv, true, t))
			throw new IllegalArgumentException();
		// Copy the connection number
		long connection = IvEncoder.getConnectionNumber(iv);
		return createConnectionReader(in, true, t, connection, secret);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			TransportId t, long connection, byte[] secret) {
		return createConnectionReader(in, false, t, connection, secret);
	}

	private ConnectionReader createConnectionReader(InputStream in,
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
