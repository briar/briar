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
			byte[] secret, byte[] tag) {
		// Validate the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(secret, true);
		long segmentNumber = TagEncoder.decodeTag(tag, tagCipher, tagKey);
		tagKey.erase();
		if(segmentNumber != 0) throw new IllegalArgumentException();
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
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		Mac mac = crypto.getMac();
		IncomingEncryptionLayer decrypter = new IncomingEncryptionLayerImpl(in,
				tagCipher, frameCipher, tagKey, frameKey, false);
		// No error correction
		IncomingErrorCorrectionLayer correcter =
			new NullIncomingErrorCorrectionLayer(decrypter);
		// Create the reader
		return new ConnectionReaderImpl(correcter, mac, macKey);
	}
}
