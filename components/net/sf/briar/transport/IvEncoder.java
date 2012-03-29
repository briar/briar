package net.sf.briar.transport;

import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(long frame, int blockSize) {
		if(frame < 0 || frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		byte[] iv = new byte[blockSize];
		updateIv(iv, frame);
		return iv;
	}

	static void updateIv(byte[] iv, long frame) {
		// Encode the frame number as a uint32
		ByteUtils.writeUint32(frame, iv, 0);
	}
}
