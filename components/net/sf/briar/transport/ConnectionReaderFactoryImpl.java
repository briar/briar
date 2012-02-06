package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;

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
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Encryption
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		FrameReader encryption = new IncomingEncryptionLayerImpl(in, tagCipher,
				frameCipher, tagKey, frameKey, !initiator);
		// Authentication
		Mac mac = crypto.getMac();
		FrameReader authentication = new IncomingAuthenticationLayerImpl(
				encryption, mac, macKey);
		// Create the reader
		return new ConnectionReaderImpl(authentication);
	}
}
