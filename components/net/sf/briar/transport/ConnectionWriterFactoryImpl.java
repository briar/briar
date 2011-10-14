package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoComponent;
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
			long capacity, boolean initiator, TransportId t, long connection,
			byte[] secret) {
		// Create the encrypter
		Cipher ivCipher = crypto.getIvCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		SecretKey ivKey = crypto.deriveOutgoingIvKey(secret);
		SecretKey frameKey = crypto.deriveOutgoingFrameKey(secret);
		byte[] iv = IvEncoder.encodeIv(initiator, t, connection);
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				capacity, iv, ivCipher, frameCipher, ivKey, frameKey);
		// Create the writer
		Mac mac = crypto.getMac();
		SecretKey macKey = crypto.deriveOutgoingMacKey(secret);
		return new ConnectionWriterImpl(encrypter, mac, macKey);
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, byte[] encryptedIv, byte[] secret) {
		// Decrypt the IV
		Cipher ivCipher = crypto.getIvCipher();
		SecretKey ivKey = crypto.deriveIncomingIvKey(secret);
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
		boolean initiator = IvEncoder.getInitiatorFlag(iv);
		TransportId t = new TransportId(IvEncoder.getTransportId(iv));
		long connection = IvEncoder.getConnectionNumber(iv);
		return createConnectionWriter(out, capacity, initiator, t, connection,
				secret);
	}
}
