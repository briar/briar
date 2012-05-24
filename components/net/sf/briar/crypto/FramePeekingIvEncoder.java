package net.sf.briar.crypto;

import net.sf.briar.util.ByteUtils;

class FramePeekingIvEncoder extends FrameIvEncoder {

	// AES/CTR uses a 128-bit IV; to match the AES/GCM IV we have to append
	// the bytes 0x00, 0x00, 0x00, 0x02 (see NIST SP 800-38D, section 7.1)
	private static final int IV_LENGTH = 16;

	@Override
	public byte[] encodeIv(long frame) {
		if(frame < 0 || frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		byte[] iv = new byte[IV_LENGTH];
		iv[IV_LENGTH - 1] = 2;
		updateIv(iv, frame);
		return iv;
	}
}
