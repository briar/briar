package org.briarproject.bramble.util;

import org.briarproject.bramble.api.FormatException;

public class ByteUtils {

	/**
	 * The maximum value that can be represented as an unsigned 16-bit integer.
	 */
	public static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1

	/**
	 * The maximum value that can be represented as an unsigned 32-bit integer.
	 */
	public static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1

	/**
	 * The number of bytes needed to encode a 16-bit integer.
	 */
	public static final int INT_16_BYTES = 2;

	/**
	 * The number of bytes needed to encode a 32-bit integer.
	 */
	public static final int INT_32_BYTES = 4;

	/**
	 * The number of bytes needed to encode a 64-bit integer.
	 */
	public static final int INT_64_BYTES = 8;

	/**
	 * The maximum number of bytes needed to encode a variable-length integer.
	 */
	public static final int MAX_VARINT_BYTES = 9;

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

	private static int countSignificantBits(long src) {
		if (src < 0) throw new IllegalArgumentException();
		int bits = 0;
		while (src > 0) {
			src >>= 1;
			bits++;
		}
		return bits;
	}

	/**
	 * Returns the number of bytes needed to represent 'src' as a
	 * variable-length integer.
	 * <p>
	 * 'src' must not be negative.
	 */
	public static int getVarIntBytes(long src) {
		if (src < 0) throw new IllegalArgumentException();
		int len = Math.max(1, (countSignificantBits(src) + 6) / 7);
		if (len > MAX_VARINT_BYTES) throw new AssertionError();
		return len;
	}

	/**
	 * Writes 'src' to 'dest' as a variable-length integer, starting at
	 * 'offset', and returns the number of bytes written.
	 * <p>
	 * `src` must not be negative.
	 */
	public static int writeVarInt(long src, byte[] dest, int offset) {
		if (src < 0) throw new IllegalArgumentException();
		int len = getVarIntBytes(src);
		if (dest.length < offset + len) throw new IllegalArgumentException();
		// Work backwards from the end
		int end = offset + len - 1;
		for (int i = end; i >= offset; i--) {
			// Encode 7 bits
			dest[i] = (byte) (src & 0x7F);
			// Raise the continuation flag, except for the last byte
			if (i < end) dest[i] |= (byte) 0x80;
			// Shift out the bits that were encoded
			src >>= 7;
		}
		return len;
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

	/**
	 * Returns the length in bytes of a variable-length integer encoded in
	 * 'src' starting at 'offset'.
	 *
	 * @throws FormatException if there is not a valid variable-length integer
	 * at the specified position.
	 */
	public static int getVarIntBytes(byte[] src, int offset)
			throws FormatException {
		if (src.length < offset) throw new IllegalArgumentException();
		for (int i = 0; i < MAX_VARINT_BYTES && offset + i < src.length; i++) {
			// If the continuation flag is lowered, this is the last byte
			if ((src[offset + i] & 0x80) == 0) return i + 1;
		}
		// We've read 9 bytes or reached the end of the input without finding
		// the last byte
		throw new FormatException();
	}

	/**
	 * Reads a variable-length integer from 'src' starting at 'offset' and
	 * returns it.
	 *
	 * @throws FormatException if there is not a valid variable-length integer
	 * at the specified position.
	 */
	public static long readVarInt(byte[] src, int offset)
			throws FormatException {
		if (src.length < offset) throw new IllegalArgumentException();
		long dest = 0;
		for (int i = 0; i < MAX_VARINT_BYTES && offset + i < src.length; i++) {
			// Decode 7 bits
			dest |= src[offset + i] & 0x7F;
			// If the continuation flag is lowered, this is the last byte
			if ((src[offset + i] & 0x80) == 0) return dest;
			// Make room for the next 7 bits
			dest <<= 7;
		}
		// We've read 9 bytes or reached the end of the input without finding
		// the last byte
		throw new FormatException();
	}
}
