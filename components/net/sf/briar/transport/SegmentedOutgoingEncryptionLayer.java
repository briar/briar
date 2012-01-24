package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.ACK_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSink;
import net.sf.briar.api.transport.Segment;

class SegmentedOutgoingEncryptionLayer implements OutgoingEncryptionLayer {

	private final SegmentSink out;
	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;
	private final boolean tagEverySegment;
	private final int headerLength, maxSegmentLength;
	private final Segment segment;
	private final byte[] iv;

	private long capacity;

	SegmentedOutgoingEncryptionLayer(SegmentSink out, long capacity,
			Cipher tagCipher, Cipher segCipher, ErasableKey tagKey,
			ErasableKey segKey, boolean tagEverySegment, boolean ackHeader) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.segCipher = segCipher;
		this.tagKey = tagKey;
		this.segKey = segKey;
		this.tagEverySegment = tagEverySegment;
		if(ackHeader) headerLength = FRAME_HEADER_LENGTH + ACK_HEADER_LENGTH;
		else headerLength = FRAME_HEADER_LENGTH;
		int length = out.getMaxSegmentLength();
		if(length < TAG_LENGTH + headerLength + 1 + MAC_LENGTH)
			throw new IllegalArgumentException();
		if(length > MAX_SEGMENT_LENGTH) throw new IllegalArgumentException();
		maxSegmentLength = length - MAC_LENGTH;
		segment = new SegmentImpl(length);
		iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
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

	public int getMaxSegmentLength() {
		return maxSegmentLength;
	}
}