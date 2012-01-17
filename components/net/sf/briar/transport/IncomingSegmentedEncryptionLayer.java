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
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.api.plugins.SegmentSource;

class IncomingSegmentedEncryptionLayer implements IncomingEncryptionLayer {

	private final SegmentSource in;
	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final int blockSize;
	private final byte[] iv;
	private final Segment segment;
	private final boolean tagEverySegment;

	private boolean firstSegment = true;
	private long segmentNumber = 0L;

	IncomingSegmentedEncryptionLayer(SegmentSource in, Cipher tagCipher,
			Cipher frameCipher, ErasableKey tagKey, ErasableKey frameKey,
			boolean tagEverySegment) {
		this.in = in;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.tagEverySegment = tagEverySegment;
		blockSize = frameCipher.getBlockSize();
		if(blockSize < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		iv = IvEncoder.encodeIv(0, blockSize);
		segment = new SegmentImpl();
	}

	public boolean readSegment(Segment s) throws IOException {
		boolean tag = tagEverySegment && !firstSegment;
		try {
			// Read the segment
			if(!in.readSegment(segment)) return false;
			int offset = tag ? TAG_LENGTH : 0, length = segment.getLength();
			if(length > MAX_SEGMENT_LENGTH) throw new FormatException();
			if(length < offset + FRAME_HEADER_LENGTH + MAC_LENGTH)
				throw new FormatException();
			byte[] ciphertext = segment.getBuffer();
			// If a tag is expected then decrypt and validate it
			if(tag) {
				long seg = TagEncoder.decodeTag(ciphertext, tagCipher, tagKey);
				if(seg == -1) throw new FormatException();
				segmentNumber = seg;
			}
			// Decrypt the segment
			try {
				IvEncoder.updateIv(iv, segmentNumber);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);
				frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
				int decrypted = frameCipher.doFinal(ciphertext, offset,
						length - offset, s.getBuffer());
				if(decrypted != length - offset) throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			s.setLength(length - offset);
			s.setSegmentNumber(segmentNumber++);
			firstSegment = false;
			return true;
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
	}
}
