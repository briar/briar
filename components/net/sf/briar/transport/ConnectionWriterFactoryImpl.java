package net.sf.briar.transport;

import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, byte[] secret) {
		return createConnectionWriter(out, capacity, true, secret);
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, byte[] secret, byte[] tag) {
		// Validate the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(secret, true);
		long segmentNumber = TagEncoder.decodeTag(tag, tagCipher, tagKey);
		tagKey.erase();
		if(segmentNumber != 0) throw new IllegalArgumentException();
		return createConnectionWriter(out, capacity, false, secret);
	}

	private ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, boolean initiator, byte[] secret) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the encrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		OutgoingEncryptionLayer encrypter = new OutgoingEncryptionLayerImpl(out,
				capacity, tagCipher, frameCipher, tagKey, frameKey, false);
		// Create the writer
		Mac mac = crypto.getMac();
		return new ConnectionWriterImpl(encrypter, mac, macKey);
	}
}
