package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSink;
import net.sf.briar.api.transport.Segment;

class OutgoingSegmentedEncryptionLayer implements OutgoingEncryptionLayer {

	private final SegmentSink out;
	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;
	private final boolean tagEverySegment;
	private final byte[] iv;
	private final Segment segment;

	private long capacity;

	OutgoingSegmentedEncryptionLayer(SegmentSink out, long capacity,
			Cipher tagCipher, Cipher segCipher, ErasableKey tagKey,
			ErasableKey segKey, boolean tagEverySegment) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.segCipher = segCipher;
		this.tagKey = tagKey;
		this.segKey = segKey;
		this.tagEverySegment = tagEverySegment;
		iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		segment = new SegmentImpl();
	}

	public void writeSegment(Segment s) throws IOException {
		byte[] plaintext = s.getBuffer(), ciphertext = segment.getBuffer();
		int length = s.getLength();
		long segmentNumber = s.getSegmentNumber();
		int offset = 0;
		if(tagEverySegment || segmentNumber == 0) {
			TagEncoder.encodeTag(ciphertext, segmentNumber, tagCipher, tagKey);
			offset = TAG_LENGTH;
		}
		IvEncoder.updateIv(iv, segmentNumber);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
			int encrypted = segCipher.doFinal(plaintext, 0, length,
					ciphertext, offset);
			if(encrypted != length) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		segment.setLength(offset + length);
		try {
			out.writeSegment(segment);
		} catch(IOException e) {
			segKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= offset + length;
	}

	public void flush() throws IOException {}

	public long getRemainingCapacity() {
		return capacity;
	}
}