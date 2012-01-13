package net.sf.briar.transport;

import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.api.plugins.SegmentSink;

class SegmentedConnectionEncrypter implements ConnectionEncrypter {

	private final SegmentSink out;
	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final byte[] iv, tag;
	private final Segment segment;

	private long capacity, frame = 0L;
	private boolean tagWritten = false;

	SegmentedConnectionEncrypter(SegmentSink out, long capacity,
			Cipher tagCipher, Cipher frameCipher, ErasableKey tagKey,
			ErasableKey frameKey) {
		this.out = out;
		this.capacity = capacity;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		iv = IvEncoder.encodeIv(0, frameCipher.getBlockSize());
		// Encrypt the tag
		tag = TagEncoder.encodeTag(0, tagCipher, tagKey);
		tagKey.erase();
		segment = new SegmentImpl();
	}

	public void writeFrame(byte[] b, int len) throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int offset = 0;
		if(!tagWritten) {
			if(tag.length + len > MAX_FRAME_LENGTH)
				throw new IllegalArgumentException();
			System.arraycopy(tag, 0, segment.getBuffer(), 0, tag.length);
			capacity -= tag.length;
			tagWritten = true;
			offset = tag.length;
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