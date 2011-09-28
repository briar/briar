package net.sf.briar.transport;

import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

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
			long capacity, boolean initiator, int transportId, long connection,
			byte[] secret) {
		// Create the encrypter
		Cipher ivCipher = crypto.getIvCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		SecretKey ivKey = crypto.deriveOutgoingIvKey(secret);
		SecretKey frameKey = crypto.deriveOutgoingFrameKey(secret);
		byte[] iv = IvEncoder.encodeIv(initiator, transportId, connection);
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				capacity, iv, ivCipher, frameCipher, ivKey, frameKey);
		// Create the writer
		Mac mac = crypto.getMac();
		SecretKey macKey = crypto.deriveOutgoingMacKey(secret);
		return new ConnectionWriterImpl(encrypter, mac, macKey);
	}
}
