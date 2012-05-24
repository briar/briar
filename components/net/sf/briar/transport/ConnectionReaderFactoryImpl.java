package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;
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
		ByteUtils.erase(secret);
		// Create the reader
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		Cipher framePeekingCipher = crypto.getFramePeekingCipher();
		IvEncoder frameIvEncoder = crypto.getFrameIvEncoder();
		IvEncoder framePeekingIvEncoder = crypto.getFramePeekingIvEncoder();
		FrameReader encryption = new IncomingEncryptionLayerImpl(in, tagCipher,
				frameCipher, framePeekingCipher, frameIvEncoder,
				framePeekingIvEncoder, tagKey, frameKey, !initiator);
		return new ConnectionReaderImpl(encryption);
	}
}
