package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class Crc32 {

	private static final long[] TABLE = new long[256];

	static {
		for (int i = 0; i < 256; i++) {
			long c = i;
			for (int j = 0; j < 8; j++) {
				if ((c & 1) != 0) c = 0xedb88320L ^ (c >> 1);
				else c >>= 1;
			}
			TABLE[i] = c;
		}
	}

	private static long update(long c, byte[] b, int off, int len) {
		for (int i = off; i < off + len; i++)
			c = TABLE[(int) ((c ^ b[i]) & 0xff)] ^ (c >> 8);
		return c;
	}

	static long crc(byte[] b, int off, int len) {
		return update(0xffffffffL, b, off, len) ^ 0xffffffffL;
	}
}
