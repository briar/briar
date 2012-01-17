package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

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

	private long frame = 0L;

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

	public int readFrame(byte[] b) throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		boolean tag = tagEverySegment && frame > 0;
		// Clear the buffer before exposing it to the transport plugin
		segment.clear();
		try {
			// Read the segment
			if(!in.readSegment(segment)) return -1;
			int offset = tag ? TAG_LENGTH : 0, length = segment.getLength();
			if(length > MAX_SEGMENT_LENGTH) throw new FormatException();
			if(length < offset + FRAME_HEADER_LENGTH + MAC_LENGTH)
				throw new FormatException();
			// If a tag is expected, decrypt and validate it
			if(tag && !TagEncoder.validateTag(segment.getBuffer(), frame,
					tagCipher, tagKey)) throw new FormatException();
			// Decrypt the frame
			try {
				IvEncoder.updateIv(iv, frame);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);
				frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
				int decrypted = frameCipher.doFinal(segment.getBuffer(), offset,
						length - offset, b);
				if(decrypted != length - offset) throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			// Validate and parse the header
			if(!HeaderEncoder.validateHeader(b, frame))
				throw new FormatException();
			int payload = HeaderEncoder.getPayloadLength(b);
			int padding = HeaderEncoder.getPaddingLength(b);
			if(length != offset + FRAME_HEADER_LENGTH + payload + padding
					+ MAC_LENGTH) throw new FormatException();
			frame++;
			return length - offset;
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
	}
}
