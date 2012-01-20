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
			byte[] secret, byte[] bufferedTag) {
		return createConnectionReader(in, secret, bufferedTag, true);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			byte[] secret) {
		return createConnectionReader(in, secret, null, false);
	}

	private ConnectionReader createConnectionReader(InputStream in,
			byte[] secret, byte[] bufferedTag, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey segKey = crypto.deriveSegmentKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher segCipher = crypto.getSegmentCipher();
		IncomingEncryptionLayer encryption = new IncomingEncryptionLayerImpl(in,
				tagCipher, segCipher, tagKey, segKey, false, bufferedTag);
		// No error correction
		IncomingErrorCorrectionLayer correction =
			new NullIncomingErrorCorrectionLayer(encryption);
		// Create the authenticator
		Mac mac = crypto.getMac();
		IncomingAuthenticationLayer authentication =
			new IncomingAuthenticationLayerImpl(correction, mac, macKey);
		// No reordering or retransmission
		IncomingReliabilityLayer reliability =
			new NullIncomingReliabilityLayer(authentication);
		// Create the reader - don't tolerate errors
		return new ConnectionReaderImpl(reliability, false);
	}

	public ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret, Segment bufferedSegment) {
		return createConnectionReader(in, secret, bufferedSegment, true);
	}

	public ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret) {
		return createConnectionReader(in, secret, null, false);
	}

	private ConnectionReader createConnectionReader(SegmentSource in,
			byte[] secret, Segment bufferedSegment, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey segKey = crypto.deriveSegmentKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher segCipher = crypto.getSegmentCipher();
		IncomingEncryptionLayer encryption =
			new IncomingSegmentedEncryptionLayer(in, tagCipher, segCipher,
					tagKey, segKey, false, bufferedSegment);
		// No error correction
		IncomingErrorCorrectionLayer correction =
			new NullIncomingErrorCorrectionLayer(encryption);
		// Create the authenticator
		Mac mac = crypto.getMac();
		IncomingAuthenticationLayer authentication =
			new IncomingAuthenticationLayerImpl(correction, mac, macKey);
		// No reordering or retransmission
		IncomingReliabilityLayer reliability =
			new NullIncomingReliabilityLayer(authentication);
		// Create the reader - don't tolerate errors
		return new ConnectionReaderImpl(reliability, false);
	}
}
