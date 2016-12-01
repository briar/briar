package org.briarproject.bramble.util;

public class ByteUtils {

	/**
	 * The maximum value that can be represented as an unsigned 16-bit integer.
	 */
	public static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1

	/**
	 * The maximum value that can be represented as an unsigned 32-bit integer.
	 */
	public static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1

	/** The number of bytes needed to encode a 16-bit integer. */
	public static final int INT_16_BYTES = 2;

	/** The number of bytes needed to encode a 32-bit integer. */
	public static final int INT_32_BYTES = 4;

	/** The number of bytes needed to encode a 64-bit integer. */
	public static final int INT_64_BYTES = 8;

	public static void writeUint16(int src, byte[] dest, int offset) {
		if (src < 0) throw new IllegalArgumentException();
		if (src > MAX_16_BIT_UNSIGNED) throw new IllegalArgumentException();
		if (dest.length < offset + INT_16_BYTES)
			throw new IllegalArgumentException();
		dest[offset] = (byte) (src >> 8);
		dest[offset + 1] = (byte) (src & 0xFF);
	}

	public static void writeUint32(long src, byte[] dest, int offset) {
		if (src < 0) throw new IllegalArgumentException();
		if (src > MAX_32_BIT_UNSIGNED) throw new IllegalArgumentException();
		if (dest.length < offset + INT_32_BYTES)
			throw new IllegalArgumentException();
		dest[offset] = (byte) (src >> 24);
		dest[offset + 1] = (byte) (src >> 16 & 0xFF);
		dest[offset + 2] = (byte) (src >> 8 & 0xFF);
		dest[offset + 3] = (byte) (src & 0xFF);
	}

	public static void writeUint64(long src, byte[] dest, int offset) {
		if (src < 0) throw new IllegalArgumentException();
		if (dest.length < offset + INT_64_BYTES)
			throw new IllegalArgumentException();
		dest[offset] = (byte) (src >> 56);
		dest[offset + 1] = (byte) (src >> 48 & 0xFF);
		dest[offset + 2] = (byte) (src >> 40 & 0xFF);
		dest[offset + 3] = (byte) (src >> 32 & 0xFF);
		dest[offset + 4] = (byte) (src >> 24 & 0xFF);
		dest[offset + 5] = (byte) (src >> 16 & 0xFF);
		dest[offset + 6] = (byte) (src >> 8 & 0xFF);
		dest[offset + 7] = (byte) (src & 0xFF);
	}

	public static int readUint16(byte[] src, int offset) {
		if (src.length < offset + INT_16_BYTES)
			throw new IllegalArgumentException();
		return ((src[offset] & 0xFF) << 8) | (src[offset + 1] & 0xFF);
	}

	public static long readUint32(byte[] src, int offset) {
		if (src.length < offset + INT_32_BYTES)
			throw new IllegalArgumentException();
		return ((src[offset] & 0xFFL) << 24)
				| ((src[offset + 1] & 0xFFL) << 16)
				| ((src[offset + 2] & 0xFFL) << 8)
				| (src[offset + 3] & 0xFFL);
	}

	public static long readUint64(byte[] src, int offset) {
		if (src.length < offset + INT_64_BYTES)
			throw new IllegalArgumentException();
		return ((src[offset] & 0xFFL) << 56)
				| ((src[offset + 1] & 0xFFL) << 48)
				| ((src[offset + 2] & 0xFFL) << 40)
				| ((src[offset + 3] & 0xFFL) << 32)
				| ((src[offset + 4] & 0xFFL) << 24)
				| ((src[offset + 5] & 0xFFL) << 16)
				| ((src[offset + 6] & 0xFFL) << 8)
				| (src[offset + 7] & 0xFFL);
	}

	public static int readUint(byte[] src, int bits) {
		if (src.length << 3 < bits) throw new IllegalArgumentException();
		int dest = 0;
		for (int i = 0; i < bits; i++) {
			if ((src[i >> 3] & 128 >> (i & 7)) != 0) dest |= 1 << bits - i - 1;
		}
		if (dest < 0) throw new AssertionError();
		if (dest >= 1 << bits) throw new AssertionError();
		return dest;
	}
}
