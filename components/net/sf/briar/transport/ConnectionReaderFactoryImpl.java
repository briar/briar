package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import net.sf.briar.api.crypto.ErasableKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
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
			TransportIndex i, byte[] encryptedIv, byte[] secret) {
		// Decrypt the IV
		Cipher ivCipher = crypto.getIvCipher();
		ErasableKey ivKey = crypto.deriveIvKey(secret, true);
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
		if(!IvEncoder.validateIv(iv, true, i))
			throw new IllegalArgumentException();
		// Copy the connection number
		long connection = IvEncoder.getConnectionNumber(iv);
		return createConnectionReader(in, true, i, connection, secret);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			TransportIndex i, long connection, byte[] secret) {
		return createConnectionReader(in, false, i, connection, secret);
	}

	private ConnectionReader createConnectionReader(InputStream in,
			boolean initiator, TransportIndex i, long connection,
			byte[] secret) {
		// Derive the keys and erase the secret
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		for(int j = 0; j < secret.length; j++) secret[j] = 0;
		// Create the decrypter
		byte[] iv = IvEncoder.encodeIv(initiator, i, connection);
		Cipher frameCipher = crypto.getFrameCipher();
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in, iv,
				frameCipher, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
