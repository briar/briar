package net.sf.briar.util;

public class ByteUtils {

	/**
	 * The maximum value that can be represented as an unsigned 16-bit integer.
	 */
	public static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1

	/**
	 * The maximum value that can be represented as an unsigned 24-bit integer.
	 */
	public static final int MAX_24_BIT_UNSIGNED = 16777215; // 2^24 - 1

	/**
	 * The maximum value that can be represented as an unsigned 32-bit integer.
	 */
	public static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1

	public static void writeUint8(int i, byte[] b, int offset) {
		if(i < 0) throw new IllegalArgumentException();
		if(i > 255) throw new IllegalArgumentException();
		if(b.length < offset) throw new IllegalArgumentException();
		b[offset] = (byte) i;
	}

	public static void writeUint16(int i, byte[] b, int offset) {
		if(i < 0) throw new IllegalArgumentException();
		if(i > MAX_16_BIT_UNSIGNED) throw new IllegalArgumentException();
		if(b.length < offset + 2) throw new IllegalArgumentException();
		b[offset] = (byte) (i >> 8);
		b[offset + 1] = (byte) (i & 0xFF);
	}

	public static void writeUint24(long i, byte[] b, int offset) {
		if(i < 0L) throw new IllegalArgumentException();
		if(i > MAX_24_BIT_UNSIGNED) throw new IllegalArgumentException();
		if(b.length < offset + 3) throw new IllegalArgumentException();
		b[offset] = (byte) (i >> 16);
		b[offset + 1] = (byte) (i >> 8 & 0xFF);
		b[offset + 2] = (byte) (i & 0xFF);
	}

	public static void writeUint32(long i, byte[] b, int offset) {
		if(i < 0L) throw new IllegalArgumentException();
		if(i > MAX_32_BIT_UNSIGNED) throw new IllegalArgumentException();
		if(b.length < offset + 4) throw new IllegalArgumentException();
		b[offset] = (byte) (i >> 24);
		b[offset + 1] = (byte) (i >> 16 & 0xFF);
		b[offset + 2] = (byte) (i >> 8 & 0xFF);
		b[offset + 3] = (byte) (i & 0xFF);
	}

	public static int readUint16(byte[] b, int offset) {
		if(b.length < offset + 2) throw new IllegalArgumentException();
		return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
	}

	public static int readUint24(byte[] b, int offset) {
		if(b.length < offset + 3) throw new IllegalArgumentException();
		return ((b[offset] & 0xFF) << 16) | ((b[offset + 1] & 0xFF) << 8)
				| (b[offset + 2] & 0xFF);
	}

	public static long readUint32(byte[] b, int offset) {
		if(b.length < offset + 4) throw new IllegalArgumentException();
		return ((b[offset] & 0xFFL) << 24) | ((b[offset + 1] & 0xFFL) << 16)
				| ((b[offset + 2] & 0xFFL) << 8) | (b[offset + 3] & 0xFFL);
	}

	public static void erase(byte[] b) {
		for(int i = 0; i < b.length; i++) b[i] = 0;
	}

	public static int readUint(byte[] b, int bits) {
		if(b.length << 3 < bits) throw new IllegalArgumentException();
		int result = 0;
		for(int i = 0; i < bits; i++) {
			if((b[i >> 3] & 128 >> (i & 7)) != 0) result |= 1 << bits - i - 1;
		}
		assert result >= 0;
		assert result < 1 << bits;
		return result;
	}

	/**
	 * Compares two byte arrays and returns -1, 0, or +1 if the first array is
	 * less than, equal to, or greater than the second array, respectively.
	 * <p>
	 * If one of the arrays is a prefix of the other, the longer array is
	 * considered to be greater. Bytes are treated as unsigned.
	 */
	public static int compare(byte[] b1, byte[] b2) {
		for(int i = 0; i < b1.length || i < b2.length; i++) {
			if(i == b1.length) return -1;
			if(i == b2.length) return 1;
			int b1i = b1[i] & 0xff, b2i = b2[i] & 0xff;
			if(b1i < b2i) return -1;
			if(b1i > b2i) return 1;
		}
		return 0;
	}
}
