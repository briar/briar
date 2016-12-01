package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class Sec1Utils {

	static void convertToFixedLength(byte[] src, byte[] dest, int destOff,
			int destLen) {
		if (src.length < destLen) {
			int padding = destLen - src.length;
			for (int i = destOff; i < destOff + padding; i++) dest[i] = 0;
			System.arraycopy(src, 0, dest, destOff + padding, src.length);
		} else {
			int srcOff = src.length - destLen;
			System.arraycopy(src, srcOff, dest, destOff, destLen);
		}
	}
}
