package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.api.plugins.SegmentSink;

class OutgoingSegmentedEncryptionLayer implements OutgoingEncryptionLayer {

	private final SegmentSink out;
	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final boolean tagEverySegment;
	private final byte[] iv;
	private final Segment segment;

	private long capacity, frame = 0L;

	OutgoingSegmentedEncryptionLayer(SegmentSink out, long capacity,
			Cipher tagCipher, Cipher frameCipher, ErasableKey tagKey,
			ErasableKey frameKey, boolean tagEverySegment) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.tagEverySegment = tagEverySegment;
		iv = IvEncoder.encodeIv(0, frameCipher.getBlockSize());
		segment = new SegmentImpl();
	}

	public void writeFrame(byte[] b, int len) throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int offset = 0;
		if(tagEverySegment || frame == 0) {
			if(len + TAG_LENGTH > MAX_FRAME_LENGTH)
				throw new IllegalArgumentException();
			TagEncoder.encodeTag(segment.getBuffer(), frame, tagCipher, tagKey);
			offset = TAG_LENGTH;
			capacity -= TAG_LENGTH;
		}
		IvEncoder.updateIv(iv, frame);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
			int encrypted = frameCipher.doFinal(b, 0, len, segment.getBuffer(),
					offset);
			if(encrypted != len) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		segment.setLength(offset + len);
		try {
			out.writeSegment(segment);
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= len;
		frame++;
	}

	public void flush() throws IOException {}

	public long getRemainingCapacity() {
		return capacity;
	}
}