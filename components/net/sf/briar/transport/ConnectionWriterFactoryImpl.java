package net.sf.briar.transport;

import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSink;
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
			long capacity, byte[] secret, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey segKey = crypto.deriveSegmentKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the encrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher segCipher = crypto.getSegmentCipher();
		OutgoingEncryptionLayer encryption = new OutgoingEncryptionLayerImpl(
				out, capacity, tagCipher, segCipher, tagKey, segKey, false);
		// No error correction
		OutgoingErrorCorrectionLayer correction =
			new NullOutgoingErrorCorrectionLayer(encryption);
		// Authentication
		Mac mac = crypto.getMac();
		OutgoingAuthenticationLayer authentication =
			new OutgoingAuthenticationLayerImpl(correction, mac, macKey);
		// No retransmission
		OutgoingReliabilityLayer reliability =
			new NullOutgoingReliabilityLayer(authentication);
		// Create the writer
		return new ConnectionWriterImpl(reliability);
	}

	public ConnectionWriter createConnectionWriter(SegmentSink out,
			long capacity, byte[] secret, boolean initiator) {
		// Derive the keys and erase the secret
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey segKey = crypto.deriveSegmentKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the encrypter
		Cipher tagCipher = crypto.getTagCipher();
		Cipher segCipher = crypto.getSegmentCipher();
		OutgoingEncryptionLayer encryption =
			new SegmentedOutgoingEncryptionLayer(out, capacity, tagCipher,
					segCipher, tagKey, segKey, false);
		// No error correction
		OutgoingErrorCorrectionLayer correction =
			new NullOutgoingErrorCorrectionLayer(encryption);
		// Authentication
		Mac mac = crypto.getMac();
		OutgoingAuthenticationLayer authentication =
			new OutgoingAuthenticationLayerImpl(correction, mac, macKey);
		// No retransmission
		OutgoingReliabilityLayer reliability =
			new NullOutgoingReliabilityLayer(authentication);
		// Create the writer
		return new ConnectionWriterImpl(reliability);
	}
}
