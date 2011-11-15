package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import net.sf.briar.api.crypto.ErasableKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, TransportIndex i, long connection, byte[] secret) {
		return createConnectionWriter(out, capacity, true, i, connection,
				secret);
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, TransportIndex i, byte[] encryptedIv,
			byte[] secret) {
		// Decrypt the IV
		Cipher ivCipher = crypto.getIvCipher();
		ErasableKey ivKey = crypto.deriveIvKey(secret, true);
		byte[] iv;
		try {
			ivCipher.init(Cipher.DECRYPT_MODE, ivKey);
			iv = ivCipher.doFinal(encryptedIv);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
		// Validate the IV
		if(!IvEncoder.validateIv(iv, true, i))
			throw new IllegalArgumentException();
		// Copy the connection number
		long connection = IvEncoder.getConnectionNumber(iv);
		return createConnectionWriter(out, capacity, false, i, connection,
				secret);
	}

	private ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, boolean initiator, TransportIndex i, long connection,
			byte[] secret) {
		// Derive the keys and erase the secret
		ErasableKey ivKey = crypto.deriveIvKey(secret, initiator);
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		for(int j = 0; j < secret.length; j++) secret[j] = 0;
		// Create the encrypter
		Cipher ivCipher = crypto.getIvCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		byte[] iv = IvEncoder.encodeIv(initiator, i, connection);
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				capacity, iv, ivCipher, frameCipher, ivKey, frameKey);
		// Create the writer
		Mac mac = crypto.getMac();
		return new ConnectionWriterImpl(encrypter, mac, macKey);
	}
}
