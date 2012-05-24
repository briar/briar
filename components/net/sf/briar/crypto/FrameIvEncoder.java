package net.sf.briar.crypto;

import net.sf.briar.api.crypto.IvEncoder;
import net.sf.briar.util.ByteUtils;

class FrameIvEncoder implements IvEncoder {

	// AES-GCM uses a 96-bit IV; the bytes 0x00, 0x00, 0x00, 0x02 are
	// appended internally (see NIST SP 800-38D, section 7.1)
	private static final int IV_LENGTH = 12;

	public byte[] encodeIv(long frame) {
		if(frame < 0 || frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		byte[] iv = new byte[IV_LENGTH];
		updateIv(iv, frame);
		return iv;
	}

	public void updateIv(byte[] iv, long frame) {
		if(frame < 0 || frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Encode the frame number as a uint32
		ByteUtils.writeUint32(frame, iv, 0);
	}
}
