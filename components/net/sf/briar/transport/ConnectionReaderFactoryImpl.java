package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.FrameSource;
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
			byte[] secret, byte[] tag) {
		// Validate the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(secret, true);
		boolean valid = TagEncoder.validateTag(tag, 0, tagCipher, tagKey);
		tagKey.erase();
		if(!valid) throw new IllegalArgumentException();
		return createConnectionReader(in, true, secret);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			byte[] secret) {
		return createConnectionReader(in, false, secret);
	}

	private ConnectionReader createConnectionReader(InputStream in,
			boolean initiator, byte[] secret) {
		// Derive the keys and erase the secret
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		Cipher frameCipher = crypto.getFrameCipher();
		Mac mac = crypto.getMac();
		FrameSource decrypter = new ConnectionDecrypter(in,
				frameCipher, frameKey, mac.getMacLength());
		// Create the reader
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
