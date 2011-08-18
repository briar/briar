package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

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
			int transportId, long connection, byte[] secret) {
		SecretKey macKey = crypto.deriveOutgoingMacKey(secret);
		SecretKey tagKey = crypto.deriveOutgoingTagKey(secret);
		SecretKey frameKey = crypto.deriveOutgoingFrameKey(secret);
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				transportId, connection, tagCipher, frameCipher, tagKey,
				frameKey);
		return new ConnectionWriterImpl(encrypter, mac);
	}
}
