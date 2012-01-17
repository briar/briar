package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSource;
import net.sf.briar.api.transport.Segment;

class IncomingSegmentedEncryptionLayer implements IncomingEncryptionLayer {

	private final SegmentSource in;
	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;
	private final int blockSize;
	private final byte[] iv;
	private final boolean tagEverySegment;
	private final Segment segment;

	private Segment bufferedSegment;
	private boolean firstSegment = true;
	private long segmentNumber = 0L;

	IncomingSegmentedEncryptionLayer(SegmentSource in, Cipher tagCipher,
			Cipher segCipher, ErasableKey tagKey, ErasableKey segKey,
			boolean tagEverySegment, Segment s) {
		this.in = in;
		this.tagCipher = tagCipher;
		this.segCipher = segCipher;
		this.tagKey = tagKey;
		this.segKey = segKey;
		this.tagEverySegment = tagEverySegment;
		blockSize = segCipher.getBlockSize();
		if(blockSize < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		iv = IvEncoder.encodeIv(0L, blockSize);
		segment = new SegmentImpl();
		bufferedSegment = s;
	}

	public boolean readSegment(Segment s) throws IOException {
		boolean expectTag = tagEverySegment || firstSegment;
		firstSegment = false;
		try {
			// Read the segment, unless we have one buffered
			Segment segment;
			if(bufferedSegment == null) {
				segment = this.segment;
				if(!in.readSegment(segment)) return false;
			} else {
				segment = bufferedSegment;
				bufferedSegment = null;
			}
			int offset = expectTag ? TAG_LENGTH : 0;
			int length = segment.getLength();
			if(length > MAX_SEGMENT_LENGTH) throw new FormatException();
			if(length < offset + FRAME_HEADER_LENGTH + MAC_LENGTH)
				throw new FormatException();
			byte[] ciphertext = segment.getBuffer();
			// If a tag is expected then decrypt and validate it
			if(expectTag) {
				long seg = TagEncoder.decodeTag(ciphertext, tagCipher, tagKey);
				if(seg == -1) throw new FormatException();
				segmentNumber = seg;
			}
			// Decrypt the segment
			try {
				IvEncoder.updateIv(iv, segmentNumber);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);
				segCipher.init(Cipher.DECRYPT_MODE, segKey, ivSpec);
				int decrypted = segCipher.doFinal(ciphertext, offset,
						length - offset, s.getBuffer());
				if(decrypted != length - offset) throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			s.setLength(length - offset);
			s.setSegmentNumber(segmentNumber++);
			return true;
		} catch(IOException e) {
			segKey.erase();
			tagKey.erase();
			throw e;
		}
	}
}
