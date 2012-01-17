package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSource;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.Segment;
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
		return createConnectionReader(in, secret, tag, true);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			byte[] secret) {
		return createConnectionReader(in, secret, null, false);
	}

	private ConnectionReader createConnectionReader(InputStream in,
			byte[] secret, byte[] tag, boolean initiator) {
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
				tagCipher, frameCipher, tagKey, frameKey, false, tag);
		// No error correction
		IncomingErrorCorrectionLayer correcter =
			new NullIncomingErrorCorrectionLayer(decrypter);
		// Create the reader
		return new ConnectionReaderImpl(correcter, mac, macKey);
	}

	public ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret, Segment buffered) {
		return createConnectionReader(in, secret, buffered, true);
	}

	public ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret) {
		return createConnectionReader(in, secret, new SegmentImpl(), false);
	}

	private ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret, Segment buffered, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		Mac mac = crypto.getMac();
		IncomingEncryptionLayer decrypter =
			new IncomingSegmentedEncryptionLayer(in, tagCipher, frameCipher,
					tagKey, frameKey, false, buffered);
		// No error correction
		IncomingErrorCorrectionLayer correcter =
			new NullIncomingErrorCorrectionLayer(decrypter);
		// Create the reader
		return new ConnectionReaderImpl(correcter, mac, macKey);
	}
}
