package net.sf.briar.transport;

import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(long frame, int blockSize) {
		if(frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		byte[] iv = new byte[blockSize];
		updateIv(iv, frame);
		return iv;
	}

	static void updateIv(byte[] iv, long frame) {
		// Encode the frame number as a uint32, leaving 2 bytes for the counter
		ByteUtils.writeUint32(frame, iv, iv.length - 6);
	}
}
